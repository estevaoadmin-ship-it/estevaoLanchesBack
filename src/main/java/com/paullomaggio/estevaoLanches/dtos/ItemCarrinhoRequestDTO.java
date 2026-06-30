package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
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
        String observacao
) {}