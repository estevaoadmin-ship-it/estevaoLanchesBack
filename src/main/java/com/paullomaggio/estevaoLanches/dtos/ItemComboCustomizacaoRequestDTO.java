package com.paullomaggio.estevaoLanches.dtos;

import java.util.List;
import java.util.UUID;

public record ItemComboCustomizacaoRequestDTO(
    UUID comboProdutoId,
    Integer indiceOcorrencia, // Adicionado para identificar a ocorrência individual
    List<UUID> adicionaisIds,
    String observacao
) {}