package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ComboProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ComboProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.services.ComboProdutoService;
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
public class ComboProdutoController {

    @Autowired private ComboProdutoService comboProdutoService;

    @PostMapping
    public ResponseEntity<ComboProdutoResponseDTO> associarItem(@Valid @RequestBody ComboProdutoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comboProdutoService.associarProdutoAoCombo(dto));
    }

    @GetMapping("/combo/{comboId}")
    public ResponseEntity<List<ComboProdutoResponseDTO>> verEstrutura(@PathVariable UUID comboId) {
        return ResponseEntity.ok(comboProdutoService.listarEstruturaDoCombo(comboId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removerItem(@PathVariable UUID id) {
        comboProdutoService.desassociarItem(id);
        return ResponseEntity.noContent().build();
    }
}