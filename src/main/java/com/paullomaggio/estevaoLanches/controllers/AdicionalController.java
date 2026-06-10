package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.services.AdicionalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/adicionais")
public class AdicionalController {

    @Autowired
    private AdicionalService adicionalService;

    @GetMapping
    public ResponseEntity<List<Adicional>> listar() {
        List<Adicional> adicionais = adicionalService.listarTodos();
        return ResponseEntity.ok(adicionais);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Adicional> buscar(@PathVariable UUID id) {
        try {
            Adicional adicional = adicionalService.buscarPorId(id);
            return ResponseEntity.ok(adicional);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> salvar(@RequestBody Adicional adicional) {
        try {
            Adicional adicionalSalvo = adicionalService.salvar(adicional);
            return ResponseEntity.status(HttpStatus.CREATED).body(adicionalSalvo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        try {
            adicionalService.deletar(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}