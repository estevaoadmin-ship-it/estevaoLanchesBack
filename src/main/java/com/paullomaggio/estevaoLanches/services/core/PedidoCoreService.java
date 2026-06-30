package com.paullomaggio.estevaoLanches.services.core;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery; // Import adicionado
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ContaDeliveryRepository; // Import adicionado
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import com.paullomaggio.estevaoLanches.services.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails; // Import adicionado
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PedidoCoreService {

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final ContaDeliveryRepository contaDeliveryRepository; // Já injetado

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
            throw new AccessDeniedException("Acesso negado: Este pedido não pertence ao cliente autenticado.");
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    // Métodos delegados para PedidoService (mantidos como estão)

    @Transactional
    public PedidoResponseDTO processarPedidoMobile(PedidoMobileRequestDTO dto) {
        return pedidoService.processarPedidoMobile(dto);
    }

    @Transactional
    public PedidoResponseDTO receberPagamento(UUID id, PagamentoRequestDTO dto) {
        return pedidoService.receberPagamento(id, dto);
    }

    @Transactional
    public PedidoResponseDTO finalizarPedido(CheckoutRequestDTO dto) {
        return pedidoService.finalizarPedido(dto);
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
                // Tenta interpretar o username como um UUID (útil se @WithMockUser(username = "uuid-string"))
                return UUID.fromString(username);
            } catch (IllegalArgumentException e) {
                // Se não for um UUID, assume que é um email e busca a ContaDelivery
                ContaDelivery contaDelivery = contaDeliveryRepository.findByEmail(username)
                        .orElseThrow(() -> new ResourceNotFoundException("Conta de delivery não encontrada para o usuário autenticado: " + username));
                return contaDelivery.getCliente().getId();
            }
        } else if (principal instanceof String email) { // Adicionado para cobrir o caso de principal ser apenas String (email)
            ContaDelivery contaDelivery = contaDeliveryRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Conta de delivery não encontrada para o email: " + email));
            return contaDelivery.getCliente().getId();
        } else {
            throw new AccessDeniedException("Tipo de principal não suportado para listar histórico de delivery.");
        }
    }
}