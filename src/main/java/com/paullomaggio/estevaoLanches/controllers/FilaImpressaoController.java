package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.FilaImpressaoDTO;
import com.paullomaggio.estevaoLanches.entities.FilaImpressao;
import com.paullomaggio.estevaoLanches.services.FilaImpressaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fila-impressao")
@CrossOrigin(origins = "*")
@Tag(name = "Fila de Impressão", description = "Gerenciamento da fila de documentos para impressão térmica")
public class FilaImpressaoController {

    private final FilaImpressaoService service;

    public FilaImpressaoController(FilaImpressaoService service) {
        this.service = service;
    }

    @Operation(summary = "Lista todos os itens pendentes na fila de impressão",
               description = "Retorna uma lista de todos os documentos que aguardam ser impressos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de itens pendentes retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @GetMapping("/pendentes")
    public ResponseEntity<List<FilaImpressaoDTO>> listarPendentes() {
        return ResponseEntity.ok(service.buscarPendentes());
    }

    @Operation(summary = "Marca um item da fila de impressão como 'processando'",
               description = "Altera o status de um item na fila para indicar que a impressão foi iniciada.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Item marcado como processando com sucesso"),
            @ApiResponse(responseCode = "404", description = "Item da fila não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @PatchMapping("/{id}/processando")
    public ResponseEntity<Void> marcarComoProcessando(@PathVariable UUID id) {
        service.alterarParaProcessando(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Confirma a impressão de um item da fila",
               description = "Marca um item na fila como impresso com sucesso.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Impressão confirmada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Item da fila não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @PatchMapping("/{id}/sucesso")
    public ResponseEntity<Void> confirmarImpressao(@PathVariable UUID id) {
        service.marcarComoImpresso(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reverte um item da fila para o status 'pendente'",
               description = "Utilizado para reverter um item para o estado pendente, por exemplo, em caso de falha na impressão.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Item revertido para pendente com sucesso"),
            @ApiResponse(responseCode = "404", description = "Item da fila não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão")
    })
    @PatchMapping("/{id}/reverter")
    public ResponseEntity<Void> reverterParaPendente(@PathVariable UUID id) {
        service.reverterParaPendente(id);
        return ResponseEntity.noContent().build();
    }
}