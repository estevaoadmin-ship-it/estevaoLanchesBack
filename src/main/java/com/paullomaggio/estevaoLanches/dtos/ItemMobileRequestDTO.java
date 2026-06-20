package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ItemMobileRequestDTO(
        @NotNull(message = "ID do produto é obrigatório")
        UUID produtoId,

        @NotNull(message = "A quantidade é obrigatória")
        Integer quantidade,

        String observacao,
        List<UUID> adicionaisIds
) {}