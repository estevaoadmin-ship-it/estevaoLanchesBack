package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import java.math.BigDecimal;
import java.util.UUID;

public record ItemPedidoResponseDTO(
        UUID id,
        String produtoNome,
        Integer quantidade,
        BigDecimal precoUnitarioHistorico, // O Preço Blindado!
        String observacaoItem
) {
    public ItemPedidoResponseDTO(ItemPedido item) {
        this(item.getId(), item.getProduto().getNome(), item.getQuantidade(), item.getPrecoUnitario(), item.getObservacaoItem());
    }
}