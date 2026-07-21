package com.paullomaggio.estevaoLanches.dtos;

import java.util.List;
import java.util.UUID;

public record ItemComboCustomizacaoRequestDTO(
    UUID comboProdutoId,
    List<UUID> adicionaisIds
) {}
