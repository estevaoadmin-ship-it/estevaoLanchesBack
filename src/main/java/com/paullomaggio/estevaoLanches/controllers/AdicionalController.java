package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.AdicionalRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.AdicionalResponseDTO;
import com.paullomaggio.estevaoLanches.services.AdicionalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}