package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ComandaResponseDTO;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.services.ComandaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/comandas")
@CrossOrigin(origins = "*")
public class ComandaController {

    @Autowired
    private ComandaService comandaService;

    @PostMapping("/abrir/{numeroMesa}")
    public ResponseEntity<ComandaResponseDTO> abrirComanda(@PathVariable Integer numeroMesa) {
        return ResponseEntity.ok(comandaService.abrirPorNumeroMesa(numeroMesa));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComandaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(comandaService.buscarPorId(id));
    }

    @GetMapping("/ativas")
    public ResponseEntity<List<ComandaResponseDTO>> listarTodasAtivas() {
        return ResponseEntity.ok(comandaService.listarTodasAtivas());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ComandaResponseDTO> alterarStatus(@PathVariable UUID id, @RequestParam StatusComanda novoStatus) {
        return ResponseEntity.ok(comandaService.alterarStatus(id, novoStatus));
    }

    @PutMapping("/{id}/fechar")
    public ResponseEntity<ComandaResponseDTO> fecharComanda(@PathVariable UUID id) {
        return ResponseEntity.ok(comandaService.fecharComanda(id));
    }
}