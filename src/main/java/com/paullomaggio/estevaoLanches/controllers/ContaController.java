package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ContaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ContaResponseDTO;
import com.paullomaggio.estevaoLanches.services.ContaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/contas")
@CrossOrigin(origins = "*")
public class ContaController {

    @Autowired private ContaService contaService;

    @PostMapping
    public ResponseEntity<ContaResponseDTO> criar(@Valid @RequestBody ContaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contaService.criar(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(contaService.buscarPorId(id));
    }

    /**
     * 🎯 REAJUSTADO: Endpoint mapeia a listagem das contas a partir da comanda mãe.
     */
    @GetMapping("/comanda/{comandaId}")
    public ResponseEntity<List<ContaResponseDTO>> listarPorComanda(@PathVariable UUID comandaId) {
        return ResponseEntity.ok(contaService.listarPorComanda(comandaId));
    }

    @PutMapping("/{id}/pagar")
    public ResponseEntity<ContaResponseDTO> liquidarConta(@PathVariable UUID id) {
        return ResponseEntity.ok(contaService.liquidarConta(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        contaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}