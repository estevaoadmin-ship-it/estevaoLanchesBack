package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CaixaFechamentoRequestDTO(
        @NotNull(message = "O valor contado fisicamente na gaveta é obrigatório.")
        @PositiveOrZero(message = "O valor contado não pode ser negativo.")
        BigDecimal valorFechamento,

        String justificativaDiferenca // 🚀 corrigido: sem espaços no nome do campo
) {}