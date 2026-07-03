package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import com.paullomaggio.estevaoLanches.services.especialistas.PedidoBalcaoService;
import com.paullomaggio.estevaoLanches.services.especialistas.PedidoMesaService;
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
@RequestMapping("/api/pedidos")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Operações relacionadas à gestão de pedidos (PDV, Mobile, Cozinha)")
public class PedidoController {

    private final PedidoCoreService coreService;
    private final PedidoBalcaoService balcaoService;
    private final PedidoMesaService mesaService;

    // === BALCÃO ===

    @Operation(summary = "Finaliza um pedido no balcão (PDV)",
            description = "Processa o checkout de um pedido feito diretamente no balcão, gerando o pedido e o pagamento.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido finalizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de checkout inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PostMapping("/balcao/checkout")
    public ResponseEntity<PedidoResponseDTO> finalizarPedidoBalcao(@RequestBody @Valid CheckoutBalcaoRequestDTO dto) {
        return ResponseEntity.ok(balcaoService.checkoutBalcao(dto));
    }

    // === MOBILE / MESA ===

    @Operation(summary = "Finaliza um pedido via aplicativo mobile ou mesa",
            description = "Processa um pedido originado de um aplicativo mobile ou de uma mesa, criando o pedido no sistema.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido mobile processado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de pedido mobile inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @PostMapping("/mobile")
    public ResponseEntity<PedidoResponseDTO> finalizarPedidoMobile(@RequestBody @Valid PedidoMobileRequestDTO dto) {
        return ResponseEntity.ok(coreService.processarPedidoMobile(dto));
    }

    @Operation(summary = "Busca itens de pedido por ID da comanda",
            description = "Retorna a lista de itens de um pedido associado a uma comanda mestra específica.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Itens da comanda retornados com sucesso"),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping("/comanda/{comandaId}")
    public ResponseEntity<List<ItemComandaMobileResponseDTO>> buscarItensPorComanda(@PathVariable UUID comandaId) {
        return ResponseEntity.ok(coreService.buscarItensPorComandaMestre(comandaId));
    }

    // === OPERAÇÕES DE CAIXA E MONITOR ===

    @Operation(summary = "Lista pedidos ativos para monitoramento",
            description = "Retorna uma lista de pedidos que estão em andamento e precisam ser monitorados (ex: na cozinha ou aguardando entrega).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pedidos ativos retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN, GARCOM ou COZINHA)")
    })
    @GetMapping("/monitor")
    public ResponseEntity<List<PedidoResponseDTO>> listarPedidosAtivosMonitor() {
        return ResponseEntity.ok(coreService.listarPedidosAtivosMonitor());
    }

    @Operation(summary = "Lista todos os pedidos",
            description = "Retorna uma lista completa de todos os pedidos registrados no sistema.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de todos os pedidos retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN, GARCOM ou COZINHA)")
    })
    @GetMapping
    public ResponseEntity<List<PedidoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(coreService.listarTodos());
    }

    @Operation(summary = "Busca um pedido pelo ID",
            description = "Retorna os detalhes de um pedido específico com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN, GARCOM ou COZINHA)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(coreService.buscarPorId(id));
    }

    @Operation(summary = "Lista o histórico de pedidos de um cliente",
            description = "Retorna todos os pedidos feitos por um cliente específico, usando seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Histórico de pedidos do cliente retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<PedidoResponseDTO>> listarHistoricoCliente(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(coreService.listarHistoricoCliente(clienteId));
    }

    @Operation(summary = "Atualiza o status de um pedido",
            description = "Altera o status de um pedido (ex: 'EM_PREPARO', 'PRONTO', 'ENTREGUE').")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status do pedido atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Status inválido ou transição não permitida"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN, GARCOM ou COZINHA)")
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<PedidoResponseDTO> atualizarStatus(@PathVariable UUID id, @RequestBody @Valid PedidoStatusRequestDTO dto) {
        return ResponseEntity.ok(coreService.atualizarStatus(id, dto));
    }

    @Operation(summary = "Registra o pagamento de um pedido",
            description = "Processa o recebimento do pagamento para um pedido, atualizando seu status financeiro.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pagamento registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de pagamento inválidos"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PutMapping("/{id}/pagar")
    public ResponseEntity<PedidoResponseDTO> receberPagamento(@PathVariable UUID id, @RequestBody @Valid PagamentoRequestDTO dto) {
        return ResponseEntity.ok(coreService.receberPagamento(id, dto));
    }

    @Operation(summary = "Cancela um pedido",
            description = "Altera o status de um pedido para 'CANCELADO'.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido cancelado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelarPedido(@PathVariable UUID id) {
        return ResponseEntity.ok(coreService.cancelarPedido(id));
    }

    // === MANIPULAÇÃO DE ITENS (PDV) ===

    @Operation(summary = "Adiciona um item a um pedido existente",
            description = "Permite adicionar um novo item a um pedido que ainda não foi finalizado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item adicionado ao pedido com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do item inválidos"),
            @ApiResponse(responseCode = "404", description = "Pedido ou produto não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PostMapping("/{pedidoId}/itens")
    public ResponseEntity<PedidoResponseDTO> adicionarItemPedido(@PathVariable UUID pedidoId, @RequestBody @Valid ItemPedidoRequestDTO dto) {
        return ResponseEntity.ok(coreService.adicionarItemPedido(pedidoId, dto));
    }

    @Operation(summary = "Remove um item de um pedido existente",
            description = "Permite remover um item específico de um pedido que ainda não foi finalizado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item removido do pedido com sucesso"),
            @ApiResponse(responseCode = "404", description = "Pedido ou item não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @DeleteMapping("/{pedidoId}/itens/{itemId}")
    public ResponseEntity<PedidoResponseDTO> removerItemPedido(@PathVariable UUID pedidoId, @PathVariable UUID itemId) {
        return ResponseEntity.ok(coreService.removerItemPedido(pedidoId, itemId));
    }

    @Operation(summary = "Atualiza adicionais de um item de pedido",
            description = "Permite modificar os adicionais de um item específico dentro de um pedido.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Adicionais do item atualizados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de adicionais inválidos"),
            @ApiResponse(responseCode = "404", description = "Pedido, item ou adicional não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PutMapping("/{pedidoId}/itens/{itemId}/adicionais")
    public ResponseEntity<PedidoResponseDTO> atualizarAdicionaisDoItem(
            @PathVariable UUID pedidoId,
            @PathVariable UUID itemId,
            @RequestBody List<UUID> adicionaisIds) {
        return ResponseEntity.ok(coreService.atualizarAdicionaisDoItem(pedidoId, itemId, adicionaisIds));
    }
}