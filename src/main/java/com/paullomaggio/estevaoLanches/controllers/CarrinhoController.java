package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.CarrinhoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemCarrinhoRequestDTO;
import com.paullomaggio.estevaoLanches.services.CarrinhoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/carrinhos")
public class CarrinhoController {

    @Autowired
    private CarrinhoService carrinhoService;

    // Substantivo "itens" na URL com método POST (Padrão RESTful elegante)
    @PostMapping("/{clienteId}/itens")
    public ResponseEntity<CarrinhoResponseDTO> adicionarItem(
            @PathVariable UUID clienteId,
            @RequestBody ItemCarrinhoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carrinhoService.adicionarItem(clienteId, dto));
    }
}