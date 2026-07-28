package com.paullomaggio.estevaoLanches.services.core;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException; // Import BusinessRuleException
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import com.paullomaggio.estevaoLanches.services.PagamentoService; // Import PagamentoService
import com.paullomaggio.estevaoLanches.services.PedidoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal; // Import BigDecimal
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoCoreService {

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final ContaDeliveryRepository contaDeliveryRepository;
    private final PagamentoService pagamentoService; // Inject PagamentoService

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarHistoricoDeliveryDoClienteAutenticado() {
        UUID clienteId = getAuthenticatedClientId();
        return pedidoRepository.findByClienteIdAndTipo(clienteId, TipoPedido.DELIVERY)
                .stream()
                .map(PedidoResponseDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPedidoDeliveryDoClienteAutenticado(UUID pedidoId) {
        UUID clienteId = getAuthenticatedClientId();
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

        if (!pedido.getCliente().getId().equals(clienteId)) {
            throw new AccessDeniedException("Acesso negado: Este pedido não pertence ao cliente autenticado.");
        }
        return new PedidoResponseDTO(pedido);
    }

    @Transactional
    public PedidoResponseDTO cancelarPedidoDeliveryDoClienteAutenticado(UUID pedidoId) {

        UUID clienteId = getAuthenticatedClientId();

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

        if (!pedido.getCliente().getId().equals(clienteId)) {
            throw new AccessDeniedException(
                    "Acesso negado: Este pedido não pertence ao cliente autenticado."
            );
        }

        BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorPedido(pedidoId);

        if (saldoLiquido.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException(
                    "Operação negada: O pedido possui pagamento ativo. Realize o estorno financeiro antes do cancelamento."
            );
        }

        pedido.setStatus(StatusPedido.CANCELADO);

        StatusFinanceiro statusFinanceiroAnterior = pedido.getStatusFinanceiro();

        if (statusFinanceiroAnterior == StatusFinanceiro.AGUARDANDO_PAGAMENTO) {
            log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setStatusFinanceiro, Pedido: {}, Conta: {}, statusAnterior: {}, statusNovo: {}, pedidoTotal: {}, thread: {}, transactionAtiva: {}",
                    getClass().getSimpleName(),
                    "cancelarPedidoDeliveryDoClienteAutenticado",
                    pedido.getId(),
                    pedido.getConta() != null ? pedido.getConta().getId() : null,
                    statusFinanceiroAnterior,
                    StatusFinanceiro.CANCELADO,
                    pedido.getTotal(),
                    Thread.currentThread().getName(),
                    org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            pedido.setStatusFinanceiro(StatusFinanceiro.CANCELADO);
        } else if (statusFinanceiroAnterior == StatusFinanceiro.ESTORNADO) {
            log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setStatusFinanceiro, Pedido: {}, Conta: {}, statusAnterior: {}, statusNovo: {}, pedidoTotal: {}, thread: {}, transactionAtiva: {}",
                    getClass().getSimpleName(),
                    "cancelarPedidoDeliveryDoClienteAutenticado",
                    pedido.getId(),
                    pedido.getConta() != null ? pedido.getConta().getId() : null,
                    statusFinanceiroAnterior,
                    StatusFinanceiro.ESTORNADO,
                    pedido.getTotal(),
                    Thread.currentThread().getName(),
                    org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            pedido.setStatusFinanceiro(StatusFinanceiro.ESTORNADO);
        } else if (statusFinanceiroAnterior == StatusFinanceiro.PAGO) {
            // A validação de saldoLiquido já garante que, se chegou aqui com PAGO,
            // o saldo líquido é zero, indicando estorno integral.
            log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: setStatusFinanceiro, Pedido: {}, Conta: {}, statusAnterior: {}, statusNovo: {}, pedidoTotal: {}, thread: {}, transactionAtiva: {}",
                    getClass().getSimpleName(),
                    "cancelarPedidoDeliveryDoClienteAutenticado",
                    pedido.getId(),
                    pedido.getConta() != null ? pedido.getConta().getId() : null,
                    statusFinanceiroAnterior,
                    StatusFinanceiro.ESTORNADO,
                    pedido.getTotal(),
                    Thread.currentThread().getName(),
                    org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            pedido.setStatusFinanceiro(StatusFinanceiro.ESTORNADO);
        }

        log.info("[FORENSE-SUBCONTA] Classe: {}, Método: {}, Ação: pré-save pedido, Pedido: {}, Conta: {}, statusFinanceiro: {}, pedidoTotal: {}, thread: {}, transactionAtiva: {}",
                getClass().getSimpleName(),
                "cancelarPedidoDeliveryDoClienteAutenticado",
                pedido.getId(),
                pedido.getConta() != null ? pedido.getConta().getId() : null,
                pedido.getStatusFinanceiro(),
                pedido.getTotal(),
                Thread.currentThread().getName(),
                org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
        return new PedidoResponseDTO(
                pedidoRepository.save(pedido)
        );
    }

    @Transactional
    public PedidoResponseDTO processarPedidoMobile(PedidoMobileRequestDTO dto) {
        return pedidoService.processarPedidoMobile(dto);
    }

    @Transactional
    public PedidoResponseDTO receberPagamento(UUID id, PagamentoRequestDTO dto) {
        return pedidoService.receberPagamento(id, dto);
    }

    @Transactional
    public PedidoResponseDTO adicionarItemPedido(UUID pedidoId, ItemPedidoRequestDTO dto) {
        return pedidoService.adicionarItemPedido(pedidoId, dto);
    }

    @Transactional
    public PedidoResponseDTO removerItemPedido(UUID pedidoId, UUID itemId) {
        return pedidoService.removerItemPedido(pedidoId, itemId);
    }

    @Transactional
    public PedidoResponseDTO atualizarAdicionaisDoItem(UUID pedidoId, UUID itemId, List<UUID> adicionaisIds) {
        return pedidoService.atualizarAdicionaisDoItem(pedidoId, itemId, adicionaisIds);
    }

    @Transactional
    public PedidoResponseDTO atualizarStatus(UUID id, PedidoStatusRequestDTO dto) {
        return pedidoService.atualizarStatus(id, dto);
    }

    @Transactional
    public PedidoResponseDTO cancelarPedido(UUID id) {
        return pedidoService.cancelarPedido(id);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPedidosAtivosMonitor() {
        return pedidoService.listarPedidosAtivosMonitor();
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        return pedidoService.listarTodos();
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(UUID id) {
        return pedidoService.buscarPorId(id);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarHistoricoCliente(UUID clienteId) {
        return pedidoService.listarHistoricoCliente(clienteId);
    }

    @Transactional(readOnly = true)
    public List<ItemComandaMobileResponseDTO> buscarItensPorComandaMestre(UUID comandaId) {
        return pedidoService.buscarItensPorComandaMestre(comandaId);
    }

    private UUID getAuthenticatedClientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Usuário não autenticado.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof ContaDelivery contaDelivery) {
            return contaDelivery.getCliente().getId();
        } else if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            try {
                return UUID.fromString(username);
            } catch (IllegalArgumentException e) {
                ContaDelivery contaDelivery = contaDeliveryRepository.findByEmail(username)
                        .orElseThrow(() -> new ResourceNotFoundException("Conta de delivery não encontrada para o usuário autenticado: " + username));
                return contaDelivery.getCliente().getId();
            }
        } else if (principal instanceof String email) {
            ContaDelivery contaDelivery = contaDeliveryRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Conta de delivery não encontrada para o email: " + email));
            return contaDelivery.getCliente().getId();
        } else {
            throw new AccessDeniedException("Tipo de principal não suportado para listar histórico de delivery.");
        }
    }
}
