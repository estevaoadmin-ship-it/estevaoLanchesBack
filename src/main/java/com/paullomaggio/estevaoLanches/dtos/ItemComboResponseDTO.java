package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ItemCombo;
import java.math.BigDecimal;
import java.util.UUID;

public record ItemComboResponseDTO(
        UUID id,
        UUID itemPedidoId,
        UUID produtoId,
        String nomeProduto,
        Integer quantidade,
        BigDecimal precoUnitario
) {
    public ItemComboResponseDTO(ItemCombo entity) {
        this(
                entity.getId(),
                entity.getItemPedido() != null ? entity.getItemPedido().getId() : null,
                entity.getProdutoId(),
                entity.getNomeProduto(),
                entity.getQuantidade(),
                entity.getPrecoUnitario()
        );
    }
}