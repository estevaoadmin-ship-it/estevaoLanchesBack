package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ComboProduto;
import java.util.UUID;

public record ComboProdutoResponseDTO(
        UUID id,
        UUID comboId,
        String comboNome,
        UUID produtoId,
        String produtoNome,
        Integer quantidade
) {
    public ComboProdutoResponseDTO(ComboProduto entity) {
        this(
                entity.getId(),
                entity.getCombo() != null ? entity.getCombo().getId() : null,
                entity.getCombo() != null ? entity.getCombo().getNome() : null,
                entity.getProduto() != null ? entity.getProduto().getId() : null,
                entity.getProduto() != null ? entity.getProduto().getNome() : null,
                entity.getQuantidade()
        );
    }
}