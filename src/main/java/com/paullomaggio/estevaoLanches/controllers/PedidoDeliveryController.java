package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.CheckoutDeliveryRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import com.paullomaggio.estevaoLanches.services.especialistas.PedidoDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/delivery/pedidos")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PedidoDeliveryController {

    private final PedidoDeliveryService deliveryService;
    private final PedidoCoreService coreService;
    private final PedidoRepository pedidoRepository;

    @PostMapping("/checkout")
    public ResponseEntity<PedidoResponseDTO> finalizarCheckoutDelivery(@RequestBody @Valid CheckoutDeliveryRequestDTO dto) {
        return ResponseEntity.ok(deliveryService.checkoutDelivery(dto));
    }

    @GetMapping("/historico/{clienteId}")
    public ResponseEntity<List<PedidoResponseDTO>> listarHistoricoDelivery(@PathVariable UUID clienteId) {
        List<PedidoResponseDTO> historico = pedidoRepository.findByClienteIdAndTipo(clienteId, TipoPedido.DELIVERY)
                .stream()
                .map(PedidoResponseDTO::new)
                .toList();
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> buscarPedidoDeliveryPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(coreService.buscarPorId(id));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelarPedidoDelivery(@PathVariable UUID id) {
        return ResponseEntity.ok(coreService.cancelarPedido(id));
    }
}