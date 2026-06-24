package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record AdicionalRequestDTO(
        @NotBlank(message = "O nome do adicional é obrigatório.")
        String nome,

        @NotNull(message = "O preço do adicional é obrigatório.")
        @PositiveOrZero(message = "O preço do adicional não pode ser negativo.")
        BigDecimal preco
) {}