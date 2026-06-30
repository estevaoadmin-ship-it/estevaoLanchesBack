package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ItemComboRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComboResponseDTO;
import com.paullomaggio.estevaoLanches.services.ItemComboService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/itens-combo-pedido")
@CrossOrigin(origins = "*")
@Tag(name = "Itens de Combo em Pedido", description = "Operações para gerenciar itens que compõem um combo dentro de um pedido")
public class ItemComboController {

    @Autowired private ItemComboService itemComboService;

    @Operation(summary = "Lança um item dentro de um combo de um pedido",
               description = "Adiciona um produto como parte de um combo já existente em um item de pedido.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Item lançado no combo com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do item combo inválidos"),
            @ApiResponse(responseCode = "404", description = "Item de pedido ou produto não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PostMapping
    public ResponseEntity<ItemComboResponseDTO> lancarItem(@Valid @RequestBody ItemComboRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemComboService.lancarItemNoCombo(dto));
    }

    @Operation(summary = "Lista os itens de um combo de pedido",
               description = "Retorna todos os produtos que fazem parte de um combo específico dentro de um item de pedido.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Itens do combo retornados com sucesso"),
            @ApiResponse(responseCode = "404", description = "Item de pedido não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/item-pedido/{itemPedidoId}")
    public ResponseEntity<List<ItemComboResponseDTO>> listarPorItemPedido(@PathVariable UUID itemPedidoId) {
        return ResponseEntity.ok(itemComboService.listarItensDoComboPedido(itemPedidoId));
    }
}