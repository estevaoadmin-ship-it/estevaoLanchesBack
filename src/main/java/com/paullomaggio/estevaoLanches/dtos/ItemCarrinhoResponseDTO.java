package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import java.math.BigDecimal;
import java.util.UUID;

public record ItemCarrinhoResponseDTO(
        UUID id,
        String produtoNome,
        Integer quantidade,
        BigDecimal precoUnitarioAtual,
        String observacao
) {
    public ItemCarrinhoResponseDTO(ItemCarrinho item) {
        this(item.getId(), item.getProduto().getNome(), item.getQuantidade(), item.getProduto().getPreco(), item.getObservacao());
    }
}