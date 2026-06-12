package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ItemPedidoRequestDTO(
        @NotNull(message = "O produto é obrigatório")
        UUID produtoId,

        @NotNull(message = "A quantidade é obrigatória")
        @Min(value = 1, message = "A quantidade mínima é 1")
        Integer quantidade,

        String observacao
) {}