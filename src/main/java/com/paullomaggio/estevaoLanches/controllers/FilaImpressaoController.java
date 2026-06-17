package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.entities.FilaImpressao;
import com.paullomaggio.estevaoLanches.services.FilaImpressaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/fila-impressao")
@CrossOrigin(origins = "*")
public class FilaImpressaoController {

    private final FilaImpressaoService service;

    public FilaImpressaoController(FilaImpressaoService service) {
        this.service = service;
    }

    @GetMapping("/pendentes")
    public ResponseEntity<List<FilaImpressao>> listarPendentes() {
        return ResponseEntity.ok(service.buscarPendentes());
    }

    @PatchMapping("/{id}/processando")
    public ResponseEntity<Void> marcarComoProcessando(@PathVariable UUID id) {
        service.alterarParaProcessando(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/sucesso")
    public ResponseEntity<Void> confirmarImpressao(@PathVariable UUID id) {
        service.marcarComoImpresso(id);
        return ResponseEntity.noContent().build();
    }
}