package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Adicional;
import java.math.BigDecimal;
import java.util.UUID;

// --- REQUEST ---
public record AdicionalRequestDTO(String nome, BigDecimal preco) {}