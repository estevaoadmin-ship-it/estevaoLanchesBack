package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.CarrinhoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemCarrinhoRequestDTO;
import com.paullomaggio.estevaoLanches.services.CarrinhoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/carrinhos")
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Carrinhos", description = "Operações relacionadas ao carrinho de compras do cliente")
public class CarrinhoController {

    @Autowired
    private CarrinhoService carrinhoService;

    @Operation(summary = "Adiciona um item ao carrinho de um cliente",
               description = "Adiciona um produto com uma quantidade específica ao carrinho de compras de um cliente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Item adicionado ao carrinho com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do item inválidos ou cliente não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @PostMapping("/{clienteId}/itens")
    public ResponseEntity<CarrinhoResponseDTO> adicionarItem(
            @PathVariable UUID clienteId,
            @RequestBody ItemCarrinhoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carrinhoService.adicionarItem(clienteId, dto));
    }

    @Operation(summary = "Atualiza a quantidade de um item no carrinho",
               description = "Modifica a quantidade de um item já existente no carrinho de compras do cliente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Quantidade do item atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Quantidade inválida"),
            @ApiResponse(responseCode = "404", description = "Carrinho ou item não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @PutMapping("/{clienteId}/itens/{itemId}/quantidade")
    public ResponseEntity<CarrinhoResponseDTO> atualizarQuantidadeItem(
            @PathVariable UUID clienteId,
            @PathVariable UUID itemId,
            @RequestParam Integer quantidade) {
        return ResponseEntity.ok(carrinhoService.atualizarQuantidadeItem(clienteId, itemId, quantidade));
    }

    @Operation(summary = "Remove um item do carrinho de um cliente",
               description = "Remove um item específico do carrinho de compras do cliente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item removido do carrinho com sucesso"),
            @ApiResponse(responseCode = "404", description = "Carrinho ou item não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @DeleteMapping("/{clienteId}/itens/{itemId}")
    public ResponseEntity<CarrinhoResponseDTO> removerItem(
            @PathVariable UUID clienteId,
            @PathVariable UUID itemId) {
        return ResponseEntity.ok(carrinhoService.removerItem(clienteId, itemId));
    }

    @Operation(summary = "Busca o carrinho de compras de um cliente",
               description = "Retorna os detalhes completos do carrinho de compras de um cliente específico.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Carrinho retornado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Carrinho não encontrado para o cliente"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas CLIENTE ou ADMIN)")
    })
    @GetMapping("/{clienteId}")
    public ResponseEntity<CarrinhoResponseDTO> buscarCarrinhoPorClienteId(@PathVariable UUID clienteId) {
        return ResponseEntity.ok(carrinhoService.buscarCarrinhoPorClienteId(clienteId));
    }
}