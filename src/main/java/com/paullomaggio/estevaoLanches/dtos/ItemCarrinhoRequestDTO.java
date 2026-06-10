package com.paullomaggio.estevaoLanches.dtos;

import java.util.UUID;

public record ItemCarrinhoRequestDTO(
        UUID produtoId,
        Integer quantidade,
        String observacao
) {}