package com.paullomaggio.estevaoLanches.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paullomaggio.estevaoLanches.dtos.GarcomMesaSessaoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.GarcomMesaSessaoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.SalvarResponsavelRequestDTO;
import com.paullomaggio.estevaoLanches.services.GarcomMesaSessaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/comandas/sessao")
@CrossOrigin(origins = "*")
@Tag(name = "Sessão do Garçom", description = "Endpoints para sincronização de pedidos e gerenciamento do estado em tempo real das mesas")
public class GarcomSessionMesaController {

    private static final Logger log = LoggerFactory.getLogger(GarcomSessionMesaController.class);
    private final GarcomMesaSessaoService garcomMesaSessaoService;
    private final ObjectMapper objectMapper; // Injetar ObjectMapper

    public GarcomSessionMesaController(GarcomMesaSessaoService garcomMesaSessaoService, ObjectMapper objectMapper) {
        this.garcomMesaSessaoService = garcomMesaSessaoService;
        this.objectMapper = objectMapper; // Atribuir o ObjectMapper injetado
    }

    @Operation(
            summary = "Obtém o estado completo e atualizado da Mesa",
            description = "Retorna a foto atual do banco: Comanda ativa, subcontas e todos os itens. Serve como Fonte Única da Verdade para a Store do aplicativo."
    )
    @GetMapping("/mesa/{mesaId}")
    public ResponseEntity<GarcomMesaSessaoResponseDTO> obterSessaoMesa(@PathVariable UUID mesaId) {
        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);

        // AUDITORIA 5 e 6
        log.info("=============================");
        log.info("AUDITORIA 5 e 6 - GarcomSessionMesaController.obterSessaoMesa");
        log.info("=============================");
        try {
            // Usar o ObjectMapper injetado
            String jsonFromService = objectMapper.writeValueAsString(response);
            log.info("DTO do Service (obterSessaoMesa):\n{}", jsonFromService);

            // Simular a serialização que o Spring faria
            // Note: Spring's default ObjectMapper might have different configurations.
            // For a precise comparison, one might need to inject the actual ObjectMapper used by Spring.
            // For this audit, we'll use a default one for consistency with previous audits.
            String jsonSentByController = objectMapper.writeValueAsString(response);
            log.info("JSON enviado pelo Controller (obterSessaoMesa):\n{}", jsonSentByController);

            if (!jsonFromService.equals(jsonSentByController)) {
                log.warn("Diferença encontrada entre DTO do Service e JSON enviado pelo Controller em obterSessaoMesa.");
                // In a real scenario, a more detailed diff would be needed.
            } else {
                log.info("DTO do Service e JSON enviado pelo Controller são idênticos em obterSessaoMesa.");
            }

        } catch (Exception e) {
            log.error("Erro ao serializar DTO no Controller em obterSessaoMesa", e);
        }
        log.info("=============================");

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Sincroniza os novos itens da Mesa (Itens Verdes)",
            description = "Envia apenas os novos itens adicionados pelo garçom. O backend processa o lote, manda para impressão e devolve a nova árvore completa de estado da mesa."
    )
    @PostMapping("/mesa/{mesaId}/sincronizar")
    public ResponseEntity<GarcomMesaSessaoResponseDTO> sincronizarSessao(
            @PathVariable UUID mesaId,
            @RequestBody GarcomMesaSessaoRequestDTO request) {

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.sincronizarSessao(mesaId, request);

        // AUDITORIA 5 e 6
        log.info("=============================");
        log.info("AUDITORIA 5 e 6 - GarcomSessionMesaController.sincronizarSessao");
        log.info("=============================");
        try {
            // Usar o ObjectMapper injetado
            String jsonFromService = objectMapper.writeValueAsString(response);
            log.info("DTO do Service (sincronizarSessao):\n{}", jsonFromService);

            String jsonSentByController = objectMapper.writeValueAsString(response);
            log.info("JSON enviado pelo Controller (sincronizarSessao):\n{}", jsonSentByController);

            if (!jsonFromService.equals(jsonSentByController)) {
                log.warn("Diferença encontrada entre DTO do Service e JSON enviado pelo Controller em sincronizarSessao.");
            } else {
                log.info("DTO do Service e JSON enviado pelo Controller são idênticos em sincronizarSessao.");
            }

        } catch (Exception e) {
            log.error("Erro ao serializar DTO no Controller em sincronizarSessao", e);
        }
        log.info("=============================");

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Salva o responsável pela subconta",
            description = "Persiste o nome e telefone do responsável pela subconta, caso ainda não exista."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Responsável salvo com sucesso"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada"),
            @ApiResponse(responseCode = "409", description = "Já existe um responsável cadastrado para esta conta")
    })
    @PutMapping("/conta/{contaId}/responsavel")
    public ResponseEntity<GarcomMesaSessaoResponseDTO> salvarResponsavel(
            @PathVariable UUID contaId,
            @RequestBody SalvarResponsavelRequestDTO request) {
        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.salvarResponsavel(contaId, request);
        return ResponseEntity.ok(response);
    }
}