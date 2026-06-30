package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Resposta contendo os detalhes de um item no carrinho de compras")
public record ItemCarrinhoResponseDTO(
        @Schema(
            description = "Identificador único do item no carrinho",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID id,
        @Schema(
            description = "Nome do produto",
            example = "Hambúrguer Clássico"
        )
        String produtoNome,
        @Schema(
            description = "Quantidade do produto",
            example = "2"
        )
        Integer quantidade,
        @Schema(
            description = "Preço unitário atual do produto",
            example = "29.90"
        )
        BigDecimal precoUnitarioAtual,
        @Schema(
            description = "Observações adicionais para este item",
            example = "Sem picles"
        )
        String observacao
) {
    public ItemCarrinhoResponseDTO(ItemCarrinho item) {
        this(item.getId(), item.getProduto().getNome(), item.getQuantidade(), item.getProduto().getPreco(), item.getObservacao());
    }
}