package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Schema(description = "DTO para adicionar ou atualizar um item no carrinho")
public record ItemCarrinhoRequestDTO(
        @Schema(
            description = "ID do produto a ser adicionado ou atualizado",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        UUID produtoId,
        @Schema(
            description = "Quantidade do produto. Se o item já existe, a quantidade será atualizada para este valor.",
            example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer quantidade,
        @Schema(
            description = "Observações adicionais para este item no carrinho",
            example = "Sem cebola"
        )
        String observacao,
        @Schema(
            description = "Lista de IDs dos adicionais selecionados para este item do carrinho",
            example = "[\"1a2b3c4d-5e6f-7890-1234-567890abcdef\", \"f1e2d3c4-b5a6-9876-5432-10fedcba9876\"]"
        )
        Set<UUID> adicionaisIds,
        @Schema(
            description = "Customizações de produtos internos de um combo, se o produto principal for um combo",
            implementation = ItemComboCustomizacaoRequestDTO.class
        )
        List<ItemComboCustomizacaoRequestDTO> itensCombo
) {
    // Construtor retrocompatível
    public ItemCarrinhoRequestDTO(
            UUID produtoId,
            Integer quantidade,
            String observacao,
            Set<UUID> adicionaisIds
    ) {
        this(
            produtoId,
            quantidade,
            observacao,
            adicionaisIds,
            null // itensCombo é nulo para retrocompatibilidade
        );
    }
}