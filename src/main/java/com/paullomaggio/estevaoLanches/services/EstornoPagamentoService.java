package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.EstornarPagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.EstornoPagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.entities.Conta;
import com.paullomaggio.estevaoLanches.entities.EstornoPagamento;
import com.paullomaggio.estevaoLanches.entities.Pagamento;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
import com.paullomaggio.estevaoLanches.repositories.ContaRepository;
import com.paullomaggio.estevaoLanches.repositories.EstornoPagamentoRepository;
import com.paullomaggio.estevaoLanches.repositories.PagamentoRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EstornoPagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final EstornoPagamentoRepository estornoPagamentoRepository;
    private final CaixaRepository caixaRepository;
    private final ContaRepository contaRepository;

    public EstornoPagamentoService(
            PagamentoRepository pagamentoRepository,
            EstornoPagamentoRepository estornoPagamentoRepository,
            CaixaRepository caixaRepository,
            ContaRepository contaRepository
    ) {
        this.pagamentoRepository = pagamentoRepository;
        this.estornoPagamentoRepository = estornoPagamentoRepository;
        this.caixaRepository = caixaRepository;
        this.contaRepository = contaRepository;
    }

    @Transactional
    public EstornoPagamentoResponseDTO estornar(
            UUID pagamentoId,
            EstornarPagamentoRequestDTO dto
    ) {
        // 1. Carregar Pagamento usando: pagamentoRepository.findByIdForUpdate(pagamentoId)
        Pagamento pagamento = pagamentoRepository.findByIdForUpdate(pagamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));

        // 3. Validar dto.valorEstornado() != null e dto.valorEstornado() > 0
        if (dto.valorEstornado() == null || dto.valorEstornado().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("O valor do estorno deve ser maior que zero.");
        }

        // 4. Calcular BigDecimal totalJaEstornado
        BigDecimal totalJaEstornado = estornoPagamentoRepository
                .somarValorEstornadoPorPagamentoId(pagamentoId);
        if (totalJaEstornado == null) {
            totalJaEstornado = BigDecimal.ZERO;
        }

        // 5. Calcular BigDecimal saldoEstornavel
        BigDecimal saldoEstornavel = pagamento.getValorPago().subtract(totalJaEstornado);

        // 6. Se saldoEstornavel <= 0, bloquear
        if (saldoEstornavel.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Este pagamento já foi totalmente estornado.");
        }

        // 7. Se dto.valorEstornado() > saldoEstornavel, bloquear
        if (dto.valorEstornado().compareTo(saldoEstornavel) > 0) {
            throw new BusinessRuleException(
                    "O valor do estorno (" + dto.valorEstornado() + ") excede o saldo estornável (" + saldoEstornavel + ")."
            );
        }

        // 8. Localizar Caixa ABERTO
        Caixa caixaAberto = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                .orElseThrow(() -> new BusinessRuleException("Não há caixa aberto para realizar o estorno."));

        // 9. Criar EstornoPagamento
        EstornoPagamento estorno = new EstornoPagamento();
        estorno.setPagamento(pagamento);
        estorno.setCaixa(caixaAberto);
        estorno.setValorEstornado(dto.valorEstornado());
        estorno.setMotivo(dto.motivo());
        estorno.setDataHora(LocalDateTime.now());

        // 10. Identidade do Usuário
        estorno.setUsuarioResponsavel(obterUsuarioResponsavelAtual());

        // 11. Persistir EstornoPagamento
        estorno = estornoPagamentoRepository.save(estorno);

        // 12. Calcular novoTotalEstornado
        BigDecimal novoTotalEstornado = totalJaEstornado.add(dto.valorEstornado());

        // 13. Calcular saldoLiquidoPagamento
        BigDecimal saldoLiquidoPagamento = pagamento.getValorPago().subtract(novoTotalEstornado);

        // 14. Sincronizar agregado associado.
        // REGRA PARA PEDIDO
        if (pagamento.getPedido() != null) {
            if (saldoLiquidoPagamento.compareTo(BigDecimal.ZERO) == 0) {
                // Estorno total
                pagamento.getPedido().setStatusFinanceiro(StatusFinanceiro.ESTORNADO);
                // Não alterar Pedido.status, Pedido.formaPagamento, Pedido.valorRecebido
            }
            // Para estorno parcial, não alterar StatusFinanceiro.PAGO
        }

        // REGRA PARA CONTA
        if (pagamento.getConta() != null) {
            Conta conta = pagamento.getConta();
            UUID contaId = conta.getId();

            BigDecimal totalBrutoPagamentosConta = pagamentoRepository.sumPagamentosPorConta(contaId);
            BigDecimal totalEstornosDosPagamentosDaConta = estornoPagamentoRepository.somarValorEstornadoPorContaId(contaId);

            BigDecimal totalLiquido = totalBrutoPagamentosConta.subtract(totalEstornosDosPagamentosDaConta);

            if (totalLiquido.compareTo(conta.getValorTotal()) >= 0) {
                conta.setPago(true);
            } else {
                conta.setPago(false);
            }
            contaRepository.save(conta); // Persistir a alteração no status da conta
        }

        return new EstornoPagamentoResponseDTO(estorno);
    }

    @Transactional(readOnly = true)
    public List<EstornoPagamentoResponseDTO> listarPorPagamento(UUID pagamentoId) {
        // Primeiro confirme que o Pagamento existe.
        pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado."));

        List<EstornoPagamento> estornos = estornoPagamentoRepository
                .findByPagamento_IdOrderByDataHoraDesc(pagamentoId);

        return estornos.stream()
                .map(EstornoPagamentoResponseDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Obtém o nome do usuário autenticado a partir do SecurityContext.
     * Retorna "SISTEMA" se não houver usuário autenticado ou se for um usuário anônimo.
     *
     * @return O nome do usuário autenticado ou "SISTEMA".
     */
    private String obterUsuarioResponsavelAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return "SISTEMA";
    }
}