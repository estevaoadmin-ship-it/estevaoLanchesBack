package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.services.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping("/checkout")
    public ResponseEntity<PedidoResponseDTO> finalizarPedido(@RequestBody @Valid CheckoutRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.finalizarPedido(dto));
    }

    @GetMapping("/monitor")
    public ResponseEntity<List<PedidoResponseDTO>> listarPedidosAtivosMonitor() {
        return ResponseEntity.ok(pedidoService.listarPedidosAtivosMonitor());
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(pedidoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(pedidoService.buscarPorId(id));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<PedidoResponseDTO>> listarHistoricoCliente(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(pedidoService.listarHistoricoCliente(clienteId));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<PedidoResponseDTO> atualizarStatus(@PathVariable UUID id, @RequestBody @Valid PedidoStatusRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.atualizarStatus(id, dto));
    }

    @PutMapping("/{id}/pagar")
    public ResponseEntity<PedidoResponseDTO> receberPagamento(@PathVariable UUID id, @RequestBody @Valid PagamentoRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.receberPagamento(id, dto));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelarPedido(@PathVariable UUID id) {
        return ResponseEntity.ok(pedidoService.cancelarPedido(id));
    }

    @PostMapping("/{pedidoId}/itens")
    public ResponseEntity<PedidoResponseDTO> adicionarItemPedido(@PathVariable UUID pedidoId, @RequestBody @Valid ItemPedidoRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.adicionarItemPedido(pedidoId, dto));
    }

    @DeleteMapping("/{pedidoId}/itens/{itemId}")
    public ResponseEntity<PedidoResponseDTO> removerItemPedido(@PathVariable UUID pedidoId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(pedidoService.removerItemPedido(pedidoId, itemId));
    }

    @PutMapping("/{pedidoId}/itens/{itemId}/adicionais")
    public ResponseEntity<PedidoResponseDTO> atualizarAdicionaisDoItem(
            @PathVariable UUID pedidoId,
            @PathVariable UUID itemId,
            @RequestBody List<UUID> adicionaisIds) {
        return ResponseEntity.ok(pedidoService.atualizarAdicionaisDoItem(pedidoId, itemId, adicionaisIds));
    }

    @PostMapping("/mobile")
    public ResponseEntity<PedidoResponseDTO> finalizarPedidoMobile(@RequestBody @Valid PedidoMobileRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.processarPedidoMobile(dto));
    }

    @GetMapping("/comanda/{comandaId}")
    public ResponseEntity<List<ItemComandaMobileResponseDTO>> buscarItensPorComanda(@PathVariable UUID comandaId) {
        return ResponseEntity.ok(pedidoService.buscarItensPorComandaMestre(comandaId));
    }
}