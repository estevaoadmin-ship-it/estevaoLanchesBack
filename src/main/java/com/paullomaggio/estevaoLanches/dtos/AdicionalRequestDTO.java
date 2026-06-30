package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

@Schema(description = "DTO para criação ou atualização de um adicional de produto")
public record AdicionalRequestDTO(
        @Schema(
            description = "Nome do adicional",
            example = "Bacon Extra",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "O nome do adicional é obrigatório.")
        String nome,

        @Schema(
            description = "Preço do adicional",
            example = "5.00",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O preço do adicional é obrigatório.")
        @PositiveOrZero(message = "O preço do adicional não pode ser negativo.")
        BigDecimal preco
) {}