package com.paullomaggio.estevaoLanches.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record CaixaFechamentoRequestDTO(
        BigDecimal valorFechamento,
        UUID usuarioId // <-- Quem está fechando?
) {}