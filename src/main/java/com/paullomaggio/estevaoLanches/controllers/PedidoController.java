package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import com.paullomaggio.estevaoLanches.services.especialistas.PedidoPDVService;
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

    private final PedidoCoreService coreService;
    private final PedidoPDVService pdvService;

    // === BALCÃO ===

    @PostMapping("/balcao/checkout")
    public ResponseEntity<PedidoResponseDTO> finalizarPedidoBalcao(@RequestBody @Valid CheckoutBalcaoRequestDTO dto) {
        return ResponseEntity.ok(pdvService.checkoutBalcao(dto));
    }

    // === MOBILE / MESA ===

    @PostMapping("/mobile")
    public ResponseEntity<PedidoResponseDTO> finalizarPedidoMobile(@RequestBody @Valid PedidoMobileRequestDTO dto) {
        return ResponseEntity.ok(pdvService.processarPedidoMobile(dto));
    }

    @GetMapping("/comanda/{comandaId}")
    public ResponseEntity<List<ItemComandaMobileResponseDTO>> buscarItensPorComanda(@PathVariable UUID comandaId) {
        return ResponseEntity.ok(coreService.buscarItensPorComandaMestre(comandaId));
    }

    // === OPERAÇÕES DE CAIXA E MONITOR ===

    @GetMapping("/monitor")
    public ResponseEntity<List<PedidoResponseDTO>> listarPedidosAtivosMonitor() {
        return ResponseEntity.ok(coreService.listarPedidosAtivosMonitor());
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(coreService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(coreService.buscarPorId(id));
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<PedidoResponseDTO>> listarHistoricoCliente(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(coreService.listarHistoricoCliente(clienteId));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<PedidoResponseDTO> atualizarStatus(@PathVariable UUID id, @RequestBody @Valid PedidoStatusRequestDTO dto) {
        return ResponseEntity.ok(coreService.atualizarStatus(id, dto));
    }

    @PutMapping("/{id}/pagar")
    public ResponseEntity<PedidoResponseDTO> receberPagamento(@PathVariable UUID id, @RequestBody @Valid PagamentoRequestDTO dto) {
        return ResponseEntity.ok(coreService.receberPagamento(id, dto));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelarPedido(@PathVariable UUID id) {
        return ResponseEntity.ok(coreService.cancelarPedido(id));
    }

    // === MANIPULAÇÃO DE ITENS (PDV) ===

    @PostMapping("/{pedidoId}/itens")
    public ResponseEntity<PedidoResponseDTO> adicionarItemPedido(@PathVariable UUID pedidoId, @RequestBody @Valid ItemPedidoRequestDTO dto) {
        return ResponseEntity.ok(coreService.adicionarItemPedido(pedidoId, dto));
    }

    @DeleteMapping("/{pedidoId}/itens/{itemId}")
    public ResponseEntity<PedidoResponseDTO> removerItemPedido(@PathVariable UUID pedidoId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(coreService.removerItemPedido(pedidoId, itemId));
    }

    @PutMapping("/{pedidoId}/itens/{itemId}/adicionais")
    public ResponseEntity<PedidoResponseDTO> atualizarAdicionaisDoItem(
            @PathVariable UUID pedidoId,
            @PathVariable UUID itemId,
            @RequestBody List<UUID> adicionaisIds) {
        return ResponseEntity.ok(coreService.atualizarAdicionaisDoItem(pedidoId, itemId, adicionaisIds));
    }
}