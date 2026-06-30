package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

@Schema(description = "DTO para solicitar o fechamento de um turno de caixa")
public record CaixaFechamentoRequestDTO(
        @Schema(
            description = "Valor total contado fisicamente na gaveta do caixa ao fechar",
            example = "1250.00",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O valor contado fisicamente na gaveta é obrigatório.")
        @PositiveOrZero(message = "O valor contado não pode ser negativo.")
        BigDecimal valorFechamento,

        @Schema(
            description = "Justificativa para qualquer diferença encontrada entre o valor esperado e o valor contado",
            example = "Diferença de R$5.00 devido a troco incorreto."
        )
        String justificativaDiferenca // 🚀 corrigido: sem espaços no nome do campo
) {}