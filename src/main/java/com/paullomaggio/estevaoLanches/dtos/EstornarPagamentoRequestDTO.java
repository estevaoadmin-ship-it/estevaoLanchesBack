package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EstornarPagamentoRequestDTO(

        @NotNull(message = "O valor do estorno é obrigatório")
        @DecimalMin(
                value = "0.01",
                inclusive = true,
                message = "O valor do estorno deve ser maior que zero"
        )
        BigDecimal valorEstornado,

        @NotBlank(message = "O motivo do estorno é obrigatório")
        @Size(
                max = 500,
                message = "O motivo do estorno deve ter no máximo 500 caracteres"
        )
        String motivo

) {}
