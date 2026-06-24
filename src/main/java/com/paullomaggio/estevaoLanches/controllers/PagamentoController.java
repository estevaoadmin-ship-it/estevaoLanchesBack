package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.PagamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PagamentoResponseDTO;
import com.paullomaggio.estevaoLanches.services.PagamentoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pagamentos")
@CrossOrigin(origins = "*")
public class PagamentoController {

    @Autowired private PagamentoService pagamentoService;

    @PostMapping("/conta/{contaId}")
    public ResponseEntity<PagamentoResponseDTO> receberPagamento(
            @PathVariable UUID contaId,
            @Valid @RequestBody PagamentoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagamentoService.registrarPagamento(contaId, dto));
    }

    @GetMapping("/conta/{contaId}")
    public ResponseEntity<List<PagamentoResponseDTO>> listarPorConta(@PathVariable UUID contaId) {
        return ResponseEntity.ok(pagamentoService.listarPorConta(contaId));
    }
}