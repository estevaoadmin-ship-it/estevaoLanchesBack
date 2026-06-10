package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Adicional;

import java.math.BigDecimal;
import java.util.UUID;

// --- RESPONSE ---
public record AdicionalResponseDTO(UUID id, String nome, BigDecimal preco) {
    public AdicionalResponseDTO(Adicional adicional) {
        this(adicional.getId(), adicional.getNome(), adicional.getPreco());
    }
}