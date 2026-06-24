package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ComboProdutoRequestDTO(
        @NotNull(message = "O ID do combo pai é obrigatório.")
        UUID comboId,

        @NotNull(message = "O ID do produto filho é obrigatório.")
        UUID produtoId,

        @NotNull(message = "A quantidade é obrigatória.")
        @Min(value = 1, message = "A quantidade mínima de itens no combo deve ser 1.")
        Integer quantidade
) {}