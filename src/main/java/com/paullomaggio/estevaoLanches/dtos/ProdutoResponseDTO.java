package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import java.math.BigDecimal;
import java.util.UUID;

public record ProdutoResponseDTO(
        UUID id,
        String nome,
        String descricao,
        BigDecimal preco,
        StatusProduto status,
        String categoriaNome // Devolvemos só o nome da categoria para ficar leve!
) {
    public ProdutoResponseDTO(Produto produto) {
        this(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getPreco(),
                produto.getStatus(),
                produto.getCategoria() != null ? produto.getCategoria().getNome() : null
        );
    }
}