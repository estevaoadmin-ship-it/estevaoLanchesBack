package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

@Schema(description = "DTO para solicitar a abertura de um novo turno de caixa")
public record CaixaAberturaRequestDTO(
        @Schema(
            description = "Valor inicial do fundo de troco para o caixa",
            example = "100.00",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O valor de abertura do fundo de troco é obrigatório.")
        @PositiveOrZero(message = "O valor de abertura não pode ser um número negativo.")
        BigDecimal valorAbertura
) {}