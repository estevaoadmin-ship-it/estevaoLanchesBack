package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "DTO para representar uma customização de item de combo no retorno do carrinho")
public record ItemComboCustomizacaoResponseDTO(
        @Schema(
                description = "ID do ComboProduto que foi customizado",
                format = "uuid",
                example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID comboProdutoId,
        @Schema(
                description = "Lista de IDs dos adicionais selecionados para este item de combo customizado",
                example = "[\"1a2b3c4d-5e6f-7890-1234-567890abcdef\", \"f1e2d3c4-b5a6-9876-5432-10fedcba9876\"]"
        )
        List<UUID> adicionaisIds,
        @Schema(
                description = "Observação individual para o item de combo customizado",
                example = "Sem cebola"
        )
        String observacao
) {}