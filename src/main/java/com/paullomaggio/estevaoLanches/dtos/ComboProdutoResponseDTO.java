package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ComboProduto;

// Mostra o que vem dentro do combo (Ex: "1x Coca-Cola", "1x X-Burguer")
public record ComboProdutoResponseDTO(
        String nomeProduto,
        Integer quantidade
) {
    public ComboProdutoResponseDTO(ComboProduto comboProduto) {
        this(comboProduto.getProduto().getNome(), comboProduto.getQuantidade());
    }
}