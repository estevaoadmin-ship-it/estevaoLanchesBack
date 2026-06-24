package com.paullomaggio.estevaoLanches.controllers;

import com.paullomaggio.estevaoLanches.dtos.ItemComboRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComboResponseDTO;
import com.paullomaggio.estevaoLanches.services.ItemComboService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/itens-combo-pedido")
@CrossOrigin(origins = "*")
public class ItemComboController {

    @Autowired private ItemComboService itemComboService;

    @PostMapping
    public ResponseEntity<ItemComboResponseDTO> lancarItem(@Valid @RequestBody ItemComboRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemComboService.lancarItemNoCombo(dto));
    }

    @GetMapping("/item-pedido/{itemPedidoId}")
    public ResponseEntity<List<ItemComboResponseDTO>> listarPorItemPedido(@PathVariable UUID itemPedidoId) {
        return ResponseEntity.ok(itemComboService.listarItensDoComboPedido(itemPedidoId));
    }
}