package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ComboProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ComboProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.services.ComboProdutoService;
import com.paullomaggio.estevaoLanches.dtos.ComboComposicaoRequestDTO;
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
@RequestMapping("/api/combos-config")
@CrossOrigin(origins = "*")
@Tag(name = "Configuração de Combos", description = "Operações para configurar a composição de produtos em combos")
public class ComboProdutoController {

    @Autowired private ComboProdutoService comboProdutoService;

    @Operation(summary = "Associa um produto a um combo",
               description = "Adiciona um produto como parte integrante de um combo existente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Produto associado ao combo com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de associação inválidos"),
            @ApiResponse(responseCode = "404", description = "Combo ou produto não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PostMapping
    public ResponseEntity<ComboProdutoResponseDTO> associarItem(@Valid @RequestBody ComboProdutoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comboProdutoService.associarProdutoAoCombo(dto));
    }

    @Operation(summary = "Verifica a estrutura de um combo",
               description = "Retorna a lista de todos os produtos que compõem um combo específico.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estrutura do combo retornada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Combo não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @GetMapping("/combo/{comboId}")
    public ResponseEntity<List<ComboProdutoResponseDTO>> verEstrutura(@PathVariable UUID comboId) {
        return ResponseEntity.ok(comboProdutoService.listarEstruturaDoCombo(comboId));
    }

    @Operation(summary = "Desassocia um item de um combo",
               description = "Remove um produto da composição de um combo existente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Item desassociado do combo com sucesso"),
            @ApiResponse(responseCode = "404", description = "Associação de item/combo não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removerItem(@PathVariable UUID id) {
        comboProdutoService.desassociarItem(id);
        return ResponseEntity.noContent().build();
    }
@Operation(summary = "Atualiza a composição de um combo", description = "Define a composição final desejada de um combo, sincronizando adições, remoções e quantidades.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Composição atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de composição inválidos"),
            @ApiResponse(responseCode = "404", description = "Combo não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PutMapping("/{comboId}/composicao")
    public ResponseEntity<Void> atualizarComposicao(@PathVariable UUID comboId, @Valid @RequestBody ComboComposicaoRequestDTO request) {
        comboProdutoService.atualizarComposicaoDoCombo(comboId, request.itens());
        return ResponseEntity.noContent().build();
    }
}