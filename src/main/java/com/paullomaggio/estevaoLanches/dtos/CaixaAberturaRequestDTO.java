package com.paullomaggio.estevaoLanches.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record CaixaAberturaRequestDTO(
        BigDecimal valorAbertura,
        UUID usuarioId // <-- Quem está abrindo?
) {}