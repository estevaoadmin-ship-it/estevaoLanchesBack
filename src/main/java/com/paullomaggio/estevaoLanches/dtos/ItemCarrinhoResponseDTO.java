package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
        String observacao,
        @Schema(
            description = "Lista de IDs dos adicionais selecionados para este item do carrinho",
            example = "[\"1a2b3c4d-5e6f-7890-1234-567890abcdef\", \"f1e2d3c4-b5a6-9876-5432-10fedcba9876\"]"
        )
        List<UUID> adicionaisIds,
        @Schema(
            description = "Customizações de produtos internos de um combo, se o produto principal for um combo",
            implementation = ItemComboCustomizacaoResponseDTO.class
        )
        List<ItemComboCustomizacaoResponseDTO> itensCombo
) {
    public ItemCarrinhoResponseDTO(ItemCarrinho item) {
        this(
            item.getId(),
            item.getProduto().getNome(),
            item.getQuantidade(),
            item.getProduto().getPreco(),
            item.getObservacao(),
            item.getAdicionais().stream().map(ad -> ad.getId()).collect(Collectors.toList()),
            item.getCustomizacoesCombo().stream()
                .map(custom -> new ItemComboCustomizacaoResponseDTO(
                    custom.getComboProdutoId(),
                    custom.getAdicionais().stream().map(ad -> ad.getId()).collect(Collectors.toList()),
                    custom.getObservacao()
                ))
                .collect(Collectors.toList())
        );
    }
}