package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.services.ProdutoService;
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
@RequestMapping("/api/produtos")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE
})
@Tag(name = "Produtos", description = "Operações relacionadas à gestão de produtos do cardápio")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    @Operation(summary = "Lista todos os produtos ou busca por termo",
               description = "Retorna uma lista de todos os produtos cadastrados. Pode ser filtrado por um termo de busca.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de produtos retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> listar(@RequestParam(required = false) String termo) {
        if (termo != null && !termo.isBlank()) {
            return ResponseEntity.ok(produtoService.buscarPorTermo(termo));
        }
        return ResponseEntity.ok(produtoService.listarTodos());
    }

    @Operation(summary = "Busca um produto pelo ID",
               description = "Retorna os detalhes de um produto específico com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    @Operation(summary = "Cadastra um novo produto",
               description = "Cria um novo produto no sistema com os dados fornecidos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Produto cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de produto inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> salvar(@RequestBody @Valid ProdutoRequestDTO dto) {
        ProdutoResponseDTO produtoSalvo = produtoService.salvar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoSalvo);
    }

    @Operation(summary = "Atualiza um produto existente",
               description = "Atualiza os dados de um produto existente com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de produto inválidos"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> atualizar(
            @PathVariable UUID id,
            @RequestBody @Valid ProdutoRequestDTO dto) {
        ProdutoResponseDTO produtoAtualizado = produtoService.atualizar(id, dto);
        return ResponseEntity.ok(produtoAtualizado);
    }

    @Operation(summary = "Exclui um produto",
               description = "Remove um produto do sistema com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Produto excluído com sucesso"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        produtoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}