package com.paullomaggio.estevaoLanches.dtos;

import java.util.List;
import java.util.UUID;

public record GarcomMesaSessaoRequestDTO(
        UUID comandaId,
        UUID contaSelecionadaId,
        List<ContaSyncDTO> contas
) {
    public record ContaSyncDTO(
            UUID id,
            Integer numeroConta,
            List<ItemNovoDTO> novosItens
    ) {}

    public record ItemNovoDTO(
            UUID produtoId,
            Integer quantidade,
            String observacao,
            List<UUID> adicionaisIds
    ) {}
}