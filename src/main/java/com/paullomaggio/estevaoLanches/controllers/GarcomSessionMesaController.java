package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.GarcomMesaSessaoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.GarcomMesaSessaoRequestDTO;
import com.paullomaggio.estevaoLanches.services.GarcomMesaSessaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/comandas/sessao")
@CrossOrigin(origins = "*")
@Tag(name = "Sessão do Garçom", description = "Endpoints para sincronização de pedidos e gerenciamento do estado em tempo real das mesas")
public class GarcomSessionMesaController {

    private final GarcomMesaSessaoService garcomMesaSessaoService;

    public GarcomSessionMesaController(GarcomMesaSessaoService garcomMesaSessaoService) {
        this.garcomMesaSessaoService = garcomMesaSessaoService;
    }

    @Operation(
            summary = "Obtém o estado completo e atualizado da Mesa",
            description = "Retorna a foto atual do banco: Comanda ativa, subcontas e todos os itens. Serve como Fonte Única da Verdade para a Store do aplicativo."
    )
    @GetMapping("/mesa/{mesaId}")
    public ResponseEntity<GarcomMesaSessaoResponseDTO> obterSessaoMesa(@PathVariable UUID mesaId) {
        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
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
        return ResponseEntity.ok(response);
    }
}