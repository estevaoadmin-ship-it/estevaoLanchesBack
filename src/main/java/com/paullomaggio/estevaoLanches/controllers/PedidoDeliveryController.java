package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.CheckoutDeliveryRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import com.paullomaggio.estevaoLanches.services.especialistas.PedidoDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Pedidos Delivery", description = "Operações de pedidos específicas para o aplicativo de delivery")
public class PedidoDeliveryController {

    private final PedidoDeliveryService deliveryService;
    private final PedidoCoreService pedidoCoreService;

    @Operation(summary = "Finaliza o checkout de um pedido de delivery",
               description = "Processa o checkout de um pedido feito via aplicativo de delivery, incluindo informações de entrega e pagamento.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Checkout de delivery finalizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de checkout inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @PostMapping("/checkout")
    public ResponseEntity<PedidoResponseDTO> finalizarCheckoutDelivery(@RequestBody @Valid CheckoutDeliveryRequestDTO dto) {
        return ResponseEntity.ok(deliveryService.checkoutDelivery(dto));
    }

    @Operation(summary = "Lista o histórico de pedidos de delivery do cliente autenticado",
               description = "Retorna todos os pedidos de delivery feitos pelo cliente que está autenticado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Histórico de pedidos de delivery retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @GetMapping("/historico")
    public ResponseEntity<List<PedidoResponseDTO>> listarHistoricoDelivery() {
        return ResponseEntity.ok(pedidoCoreService.listarHistoricoDeliveryDoClienteAutenticado());
    }

    @Operation(summary = "Busca um pedido de delivery pelo ID para o cliente autenticado",
               description = "Retorna os detalhes de um pedido de delivery específico, garantindo que pertence ao cliente autenticado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido de delivery encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado ou não pertence ao cliente autenticado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> buscarPedidoDeliveryPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(pedidoCoreService.buscarPedidoDeliveryDoClienteAutenticado(id));
    }

    @Operation(summary = "Cancela um pedido de delivery do cliente autenticado",
               description = "Permite que o cliente autenticado cancele um de seus pedidos de delivery, se o status permitir.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido de delivery cancelado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Não é possível cancelar o pedido neste status"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado ou não pertence ao cliente autenticado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelarPedidoDelivery(@PathVariable UUID id) {
        return ResponseEntity.ok(pedidoCoreService.cancelarPedidoDeliveryDoClienteAutenticado(id));
    }
}