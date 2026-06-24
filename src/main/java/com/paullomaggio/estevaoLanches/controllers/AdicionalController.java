package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.AdicionalRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.AdicionalResponseDTO;
import com.paullomaggio.estevaoLanches.services.AdicionalService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/adicionais")
@CrossOrigin(origins = "*")
public class AdicionalController {

    @Autowired
    private AdicionalService adicionalService;

    @GetMapping
    public ResponseEntity<List<AdicionalResponseDTO>> listar() {
        return ResponseEntity.ok(adicionalService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdicionalResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(adicionalService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<AdicionalResponseDTO> salvar(@Valid @RequestBody AdicionalRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adicionalService.salvar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdicionalResponseDTO> atualizar(@PathVariable UUID id, @Valid @RequestBody AdicionalRequestDTO dto) {
        return ResponseEntity.ok(adicionalService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        adicionalService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}