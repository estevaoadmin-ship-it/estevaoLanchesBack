package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemPedidoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoStatusRequestDTO;
import com.paullomaggio.estevaoLanches.services.PedidoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")
public class PedidoController {

    @Autowired
    private PedidoService pedidoService;

    @PostMapping("/checkout")
    public ResponseEntity<PedidoResponseDTO> criarPedido(@Valid @RequestBody CheckoutRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.finalizarPedido(dto));
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

    @GetMapping("/monitor")
    public ResponseEntity<List<PedidoResponseDTO>> listarPedidosAtivosMonitor() {
        return ResponseEntity.ok(pedidoService.listarPedidosAtivosMonitor());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<PedidoResponseDTO> atualizarStatus(@PathVariable UUID id, @Valid @RequestBody PedidoStatusRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.atualizarStatus(id, dto));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelarPedido(@PathVariable UUID id) {
        return ResponseEntity.ok(pedidoService.cancelarPedido(id));
    }

    @PostMapping("/{pedidoId}/itens")
    public ResponseEntity<PedidoResponseDTO> adicionarItemPedido(@PathVariable UUID pedidoId, @Valid @RequestBody ItemPedidoRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.adicionarItemPedido(pedidoId, dto));
    }

    @DeleteMapping("/{pedidoId}/itens/{itemId}")
    public ResponseEntity<PedidoResponseDTO> removerItemPedido(@PathVariable UUID pedidoId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(pedidoService.removerItemPedido(pedidoId, itemId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirFisicamente(@PathVariable UUID id) {
        pedidoService.excluirFisicamente(id);
        return ResponseEntity.noContent().build();
    }
}