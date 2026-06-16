package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.AdicionalRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.AdicionalResponseDTO;
import com.paullomaggio.estevaoLanches.services.AdicionalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/adicionais")
@CrossOrigin(origins = "http://localhost:4200")
public class AdicionalController {

    @Autowired
    private AdicionalService adicionalService;

    @GetMapping
    public ResponseEntity<List<AdicionalResponseDTO>> listar() {
        return ResponseEntity.ok(adicionalService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<AdicionalResponseDTO> salvar(@RequestBody AdicionalRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adicionalService.salvar(dto));
    }

    //  ADICIONADO: Rota que libera a edição de adicionais
    @PutMapping("/{id}")
    public ResponseEntity<AdicionalResponseDTO> atualizar(@PathVariable UUID id, @RequestBody AdicionalRequestDTO dto) {
        return ResponseEntity.ok(adicionalService.atualizar(id, dto));
    }

    //  ADICIONADO: Rota que libera a exclusão de adicionais
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        adicionalService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}