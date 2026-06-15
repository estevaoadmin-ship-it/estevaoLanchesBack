package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CaixaAberturaRequestDTO(
        @NotNull(message = "O valor de abertura do fundo de troco é obrigatório.")
        @PositiveOrZero(message = "O valor de abertura não pode ser um número negativo.")
        BigDecimal valorAbertura
) {}