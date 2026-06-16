package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;

public record ItemPedidoRequestDTO(
        @NotNull(message = "O ID do produto e obrigatorio.")
        UUID produtoId,

        @NotNull(message = "A quantidade e obrigatoria.")
        @Min(value = 1, message = "A quantidade minima deve ser 1.")
        Integer quantidade,

        String observacao,

        List<UUID> adicionaisIds // Adicionado para permitir a insercao de adicionais na edicao por telefone
) {}