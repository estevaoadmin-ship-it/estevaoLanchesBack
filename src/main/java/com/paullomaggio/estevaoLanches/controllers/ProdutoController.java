package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.services.ProdutoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    @Autowired
    private ProdutoService produtoService;

    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> listar() {
        return ResponseEntity.ok(produtoService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> salvar(@RequestBody ProdutoRequestDTO dto) {
        // Repare que recebemos um RequestDTO e devolvemos um ResponseDTO
        ProdutoResponseDTO produtoSalvo = produtoService.salvar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoSalvo);
    }
}