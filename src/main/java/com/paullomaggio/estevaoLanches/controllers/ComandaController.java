package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ComandaResponseDTO;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.services.ComandaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/comandas")
@CrossOrigin(origins = "*")
@Tag(name = "Comandas", description = "Operações relacionadas à gestão de comandas de mesa")
public class ComandaController {

    @Autowired
    private ComandaService comandaService;

    @Operation(summary = "Abre uma nova comanda para uma mesa",
               description = "Cria e abre uma nova comanda associada a um número de mesa específico.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comanda aberta com sucesso"),
            @ApiResponse(responseCode = "400", description = "Mesa já possui comanda ativa ou número de mesa inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PostMapping("/abrir/{numeroMesa}")
    public ResponseEntity<ComandaResponseDTO> abrirComanda(@PathVariable Integer numeroMesa) {
        return ResponseEntity.ok(comandaService.abrirPorNumeroMesa(numeroMesa));
    }

    @Operation(summary = "Busca uma comanda pelo ID",
               description = "Retorna os detalhes de uma comanda específica com base no seu ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comanda encontrada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ComandaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(comandaService.buscarPorId(id));
    }

    @Operation(summary = "Lista todas as comandas ativas",
               description = "Retorna uma lista de todas as comandas que estão atualmente abertas ou em uso.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de comandas ativas retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @GetMapping("/ativas")
    public ResponseEntity<List<ComandaResponseDTO>> listarTodasAtivas() {
        return ResponseEntity.ok(comandaService.listarTodasAtivas());
    }

    @Operation(summary = "Altera o status de uma comanda",
               description = "Atualiza o status de uma comanda para um novo estado (ex: 'FECHADA', 'CANCELADA').")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status da comanda alterado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Status inválido ou transição não permitida"),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<ComandaResponseDTO> alterarStatus(@PathVariable UUID id, @RequestParam StatusComanda novoStatus) {
        return ResponseEntity.ok(comandaService.alterarStatus(id, novoStatus));
    }

    @Operation(summary = "Fecha uma comanda",
               description = "Altera o status de uma comanda para 'FECHADA'.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comanda fechada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão (apenas ADMIN ou GARCOM)")
    })
    @PutMapping("/{id}/fechar")
    public ResponseEntity<ComandaResponseDTO> fecharComanda(@PathVariable UUID id) {
        return ResponseEntity.ok(comandaService.fecharComanda(id));
    }
}