package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.PagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.entities.Conta;
import com.paullomaggio.estevaoLanches.entities.Pagamento;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaRepository;
import com.paullomaggio.estevaoLanches.repositories.EstornoPagamentoRepository;
import com.paullomaggio.estevaoLanches.repositories.PagamentoRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final ContaRepository contaRepository;
    private final CaixaRepository caixaRepository;
    private final EstornoPagamentoRepository estornoPagamentoRepository;
    private final PedidoRepository pedidoRepository;

    public PagamentoService(PagamentoRepository pagamentoRepository, ContaRepository contaRepository, CaixaRepository caixaRepository, EstornoPagamentoRepository estornoPagamentoRepository, PedidoRepository pedidoRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.contaRepository = contaRepository;
        this.caixaRepository = caixaRepository;
        this.estornoPagamentoRepository = estornoPagamentoRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional
    public PagamentoResponseDTO registrarPagamento(UUID contaId, PagamentoRequestDTO dto) {
        Conta conta = contaRepository.findByIdForUpdate(contaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta de destino não localizada. ID: " + contaId));

        BigDecimal totalBrutoPago = pagamentoRepository.sumPagamentosPorConta(contaId);
        if (totalBrutoPago == null) totalBrutoPago = BigDecimal.ZERO;

        BigDecimal totalEstornado = estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId);
        if (totalEstornado == null) totalEstornado = BigDecimal.ZERO;

        BigDecimal totalLiquidoPago = totalBrutoPago.subtract(totalEstornado);
        BigDecimal saldoDevedor = conta.getValorTotal().subtract(totalLiquidoPago);

        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: entrada pagamento, Conta: {}, valorTotal: {}, saldoCalculado: {}, totalBrutoPago: {}, totalEstornado: {}, contaPagoAntes: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "registrarPagamento",
                conta.getId(),
                conta.getValorTotal(),
                saldoDevedor,
                totalBrutoPago,
                totalEstornado,
                conta.getPago(),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());

        if (Boolean.TRUE.equals(conta.getPago())) {
            throw new BusinessRuleException("Operação negada! Esta conta já se encontra totalmente quitada.");
        }

        BigDecimal valorRecebido = dto.valorRecebido();
        if (valorRecebido == null || valorRecebido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("O valor do pagamento deve ser maior que zero.");
        }

        Caixa caixaAberto = caixaRepository
                .findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException(
                        "Operação bloqueada! O caixa geral está fechado no momento."
                ));

        if (saldoDevedor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Operação negada! O saldo devedor atual é zero ou negativo.");
        }

        BigDecimal valorEfetivamentePago;
        if (dto.formaPagamento() == FormaPagamento.DINHEIRO) {
            valorEfetivamentePago = valorRecebido.min(saldoDevedor);
        } else {
            if (valorRecebido.compareTo(saldoDevedor) > 0) {
                throw new BusinessRuleException("Valor informado excede o saldo devedor atual.");
            }
            valorEfetivamentePago = valorRecebido;
        }

        Pagamento pagamento = new Pagamento();
        pagamento.setConta(conta);
        pagamento.setPedido(null);
        pagamento.setCaixa(caixaAberto);
        pagamento.setValorPago(valorEfetivamentePago);
        pagamento.setFormaPagamento(dto.formaPagamento());
        pagamento.setDataHora(LocalDateTime.now());
        pagamento.setUsuarioResponsavel(obterUsuarioResponsavelAtual());

        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);

        BigDecimal novoTotalLiquidoPago = totalLiquidoPago.add(valorEfetivamentePago);
        if (novoTotalLiquidoPago.compareTo(conta.getValorTotal()) >= 0) {
            log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setPago, Conta: {}, valorTotal: {}, pagoAnterior: {}, pagoNovo: {}, saldoCalculado: {}, stacktrace: {}",
                    getClass().getSimpleName(),
                    "registrarPagamento",
                    conta.getId(),
                    conta.getValorTotal(),
                    conta.getPago(),
                    true,
                    saldoDevedor,
                    stacktraceResumido());
            conta.setPago(true);
            log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pré-save conta, Conta: {}, valorTotal: {}, pago: {}, thread: {}, transactionAtiva: {}",
                    getClass().getSimpleName(),
                    "registrarPagamento",
                    conta.getId(),
                    conta.getValorTotal(),
                    conta.getPago(),
                    Thread.currentThread().getName(),
                    org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            contaRepository.save(conta);

            // Sincronizar Pedido.statusFinanceiro para PAGO
            for (Pedido pedido : conta.getPedidos()) {
                if (pedido.getStatus() != StatusPedido.CANCELADO) {
                    pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);
                    pedidoRepository.save(pedido);
                }
            }
        } else {
            log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setPago, Conta: {}, valorTotal: {}, pagoAnterior: {}, pagoNovo: {}, saldoCalculado: {}, stacktrace: {}",
                    getClass().getSimpleName(),
                    "registrarPagamento",
                    conta.getId(),
                    conta.getValorTotal(),
                    conta.getPago(),
                    false,
                    saldoDevedor,
                    stacktraceResumido());
            conta.setPago(false);
            log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pré-save conta, Conta: {}, valorTotal: {}, pago: {}, thread: {}, transactionAtiva: {}",
                    getClass().getSimpleName(),
                    "registrarPagamento",
                    conta.getId(),
                    conta.getValorTotal(),
                    conta.getPago(),
                    Thread.currentThread().getName(),
                    org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            contaRepository.save(conta);
        }

        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pós-save conta, Conta: {}, valorTotal: {}, pago: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "registrarPagamento",
                conta.getId(),
                conta.getValorTotal(),
                conta.getPago(),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());

        return new PagamentoResponseDTO(pagamentoSalvo);
    }

    @Transactional(readOnly = true)
    public List<PagamentoResponseDTO> listarPorConta(UUID contaId) {
        if (!contaRepository.existsById(contaId)) {
            throw new ResourceNotFoundException("Conta não localizada.");
        }
        return pagamentoRepository.findByContaId(contaId).stream()
                .map(PagamentoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PagamentoResponseDTO> listarPorPedido(UUID pedidoId) {
        return pagamentoRepository.findByPedidoId(pedidoId).stream()
                .map(PagamentoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public PagamentoResponseDTO registrarPagamentoPedido(
            UUID pedidoId, // Recebe o ID para buscar e lockar o Pedido
            PagamentoRequestDTO dto
    ) {
        // 1. Adquire o lock pessimista no Pedido
        Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não localizado. ID: " + pedidoId));

        BigDecimal valorRecebido = dto.valorRecebido();

        if (valorRecebido == null
                || valorRecebido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(
                    "O valor do pagamento deve ser maior que zero."
            );
        }

        // 2. Validação do saldo líquido financeiro
        BigDecimal saldoLiquidoAtual = getSaldoLiquidoPagoPorPedido(pedido.getId());

        // Para permitir um novo pagamento integral, o saldo líquido financeiro anterior deve ser ZERO.
        if (saldoLiquidoAtual.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException(
                    "Operação negada: Já existe saldo financeiro ativo para este pedido. Saldo atual: " + saldoLiquidoAtual
            );
        }

        // As validações de valor recebido em relação ao total do pedido permanecem
        if (valorRecebido.compareTo(pedido.getTotal()) < 0) {
            throw new BusinessRuleException(
                    "Valor recebido é insuficiente para quitar o pedido."
            );
        }

        if (valorRecebido.compareTo(pedido.getTotal()) > 0
                && dto.formaPagamento() != FormaPagamento.DINHEIRO) {
            throw new BusinessRuleException(
                    "Valor informado excede o total do pedido."
            );
        }

        Caixa caixaAberto = caixaRepository
                .findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException(
                        "Operação bloqueada! O caixa geral está fechado no momento."
                ));

        Pagamento pagamento = new Pagamento();
        pagamento.setPedido(pedido);
        pagamento.setConta(null);
        pagamento.setCaixa(caixaAberto);
        pagamento.setValorPago(pedido.getTotal());
        pagamento.setFormaPagamento(dto.formaPagamento());
        pagamento.setDataHora(LocalDateTime.now());
        pagamento.setUsuarioResponsavel(obterUsuarioResponsavelAtual());

        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);

        // 3. Atualiza o StatusFinanceiro do Pedido para PAGO
        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setStatusFinanceiro, Pedido: {}, Conta: {}, statusAnterior: {}, statusNovo: {}, pedidoTotal: {}, thread: {}, transactionAtiva: {}, stacktrace: {}",
                getClass().getSimpleName(),
                "registrarPagamentoPedido",
                pedido.getId(),
                pedido.getConta() != null ? pedido.getConta().getId() : null,
                pedido.getStatusFinanceiro(),
                StatusFinanceiro.PAGO,
                pedido.getTotal(),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),
                stacktraceResumido());
        pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);
        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pré-save pedido pagamento, Pedido: {}, Conta: {}, statusFinanceiro: {}, pedidoTotal: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "registrarPagamentoPedido",
                pedido.getId(),
                pedido.getConta() != null ? pedido.getConta().getId() : null,
                pedido.getStatusFinanceiro(),
                pedido.getTotal(),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        pedidoRepository.save(pedido); // Salva o pedido com o novo status financeiro

        return new PagamentoResponseDTO(pagamentoSalvo);
    }

    @Transactional(readOnly = true)
    public BigDecimal getSaldoLiquidoPagoPorConta(UUID contaId) {
        BigDecimal totalBrutoPago = pagamentoRepository.sumPagamentosPorConta(contaId);
        if (totalBrutoPago == null) totalBrutoPago = BigDecimal.ZERO;

        BigDecimal totalEstornado = estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId);
        if (totalEstornado == null) totalEstornado = BigDecimal.ZERO;

        return totalBrutoPago.subtract(totalEstornado);
    }

    @Transactional(readOnly = true)
    public BigDecimal getSaldoLiquidoPagoPorPedido(UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não localizado. ID: " + pedidoId));

        if (pedido.getConta() != null) {
            return getSaldoLiquidoPagoPorConta(pedido.getConta().getId());
        } else {
            BigDecimal totalBrutoPago = pagamentoRepository.sumPagamentosPorPedido(pedidoId);
            if (totalBrutoPago == null) totalBrutoPago = BigDecimal.ZERO;

            BigDecimal totalEstornado = BigDecimal.ZERO;
            List<Pagamento> pagamentosDoPedido = pagamentoRepository.findByPedidoId(pedidoId);
            for (Pagamento p : pagamentosDoPedido) {
                BigDecimal estornoDoPagamento = estornoPagamentoRepository.somarValorEstornadoPorPagamentoId(p.getId());
                if (estornoDoPagamento != null) {
                    totalEstornado = totalEstornado.add(estornoDoPagamento);
                }
            }
            return totalBrutoPago.subtract(totalEstornado);
        }
    }

    private String obterUsuarioResponsavelAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return "SISTEMA";
    }

    private String stacktraceResumido() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .skip(3)
                .limit(8)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(" | "));
    }
}
