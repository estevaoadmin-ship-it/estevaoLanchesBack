package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.CategoriaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CategoriaResponseDTO;
import com.paullomaggio.estevaoLanches.services.CategoriaService;
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
@RequestMapping("/api/categorias")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE
})
@Tag(name = "Categorias", description = "Operações relacionadas à gestão de categorias de produtos")
public class CategoriaController {

    @Autowired
    private CategoriaService categoriaService;

    @Operation(summary = "Lista todas as categorias ou busca por nome",
               description = "Retorna uma lista de todas as categorias de produtos. Pode ser filtrada por um termo de busca no nome.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de categorias retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> listar(@RequestParam(required = false) String nome) {
        if (nome != null && !nome.isBlank()) {
            return ResponseEntity.ok(categoriaService.buscarPorNome(nome));
        }
        return ResponseEntity.ok(categoriaService.listarTodas());
    }

    @Operation(summary = "Busca uma categoria pelo ID",
               description = "Retorna os detalhes de uma categoria específica com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Categoria encontrada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(categoriaService.buscarPorId(id));
    }

    @Operation(summary = "Cadastra uma nova categoria",
               description = "Cria uma nova categoria de produto no sistema com os dados fornecidos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Categoria cadastrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de categoria inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> salvar(@RequestBody @Valid CategoriaRequestDTO dto) {
        CategoriaResponseDTO categoriaSalva = categoriaService.salvar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaSalva);
    }

    @Operation(summary = "Atualiza uma categoria existente",
               description = "Atualiza os dados de uma categoria existente com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Categoria atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de categoria inválidos"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> atualizar(
            @PathVariable UUID id,
            @RequestBody @Valid CategoriaRequestDTO dto) {
        CategoriaResponseDTO categoriaAtualizada = categoriaService.atualizar(id, dto);
        return ResponseEntity.ok(categoriaAtualizada);
    }

    @Operation(summary = "Exclui uma categoria",
               description = "Remove uma categoria de produto do sistema com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Categoria excluída com sucesso"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        categoriaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}