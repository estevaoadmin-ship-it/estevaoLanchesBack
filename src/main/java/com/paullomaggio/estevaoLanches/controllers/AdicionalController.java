package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.AdicionalRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.AdicionalResponseDTO;
import com.paullomaggio.estevaoLanches.services.AdicionalService;
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
@RequestMapping("/api/adicionais")
@CrossOrigin(origins = "*")
@Tag(name = "Adicionais", description = "Operações relacionadas à gestão de adicionais de produtos")
public class AdicionalController {

    @Autowired
    private AdicionalService adicionalService;

    @Operation(summary = "Lista todos os adicionais",
               description = "Retorna uma lista de todos os adicionais disponíveis para produtos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de adicionais retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping
    public ResponseEntity<List<AdicionalResponseDTO>> listar() {
        return ResponseEntity.ok(adicionalService.listarTodos());
    }

    @Operation(summary = "Busca um adicional pelo ID",
               description = "Retorna os detalhes de um adicional específico com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Adicional encontrado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Adicional não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AdicionalResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(adicionalService.buscarPorId(id));
    }

    @Operation(summary = "Cria um novo adicional",
               description = "Cadastra um novo adicional no sistema.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Adicional criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do adicional inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PostMapping
    public ResponseEntity<AdicionalResponseDTO> salvar(@Valid @RequestBody AdicionalRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adicionalService.salvar(dto));
    }

    @Operation(summary = "Atualiza um adicional existente",
               description = "Atualiza os dados de um adicional existente com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Adicional atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados do adicional inválidos"),
            @ApiResponse(responseCode = "404", description = "Adicional não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @PutMapping("/{id}")
    public ResponseEntity<AdicionalResponseDTO> atualizar(@PathVariable UUID id, @Valid @RequestBody AdicionalRequestDTO dto) {
        return ResponseEntity.ok(adicionalService.atualizar(id, dto));
    }

    @Operation(summary = "Exclui um adicional",
               description = "Remove um adicional do sistema com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Adicional excluído com sucesso"),
            @ApiResponse(responseCode = "404", description = "Adicional não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        adicionalService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}