package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CaixaFechamentoRequestDTO(
        @NotNull(message = "O valor total de fechamento do caixa é obrigatório.")
        @PositiveOrZero(message = "O valor de fechamento não pode ser um número negativo.")
        BigDecimal valorFechamento
) {}