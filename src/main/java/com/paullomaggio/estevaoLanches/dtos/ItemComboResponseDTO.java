package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.ItemCombo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.paullomaggio.estevaoLanches.dtos.ItemPedidoResponseDTO.AdicionalInfo;

@Schema(description = "Resposta contendo os detalhes de um item de combo dentro de um pedido")
public record ItemComboResponseDTO(
        @Schema(
            description = "Identificador único do item de combo",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID id,
        @Schema(
            description = "ID do item de pedido pai",
            format = "uuid",
            example = "b2c3d4e5-f6a7-8901-2345-67890abcdef1"
        )
        UUID itemPedidoId,
        @Schema(
            description = "ID do produto interno do combo",
            format = "uuid",
            example = "c3d4e5f6-a7b8-9012-3456-7890abcdef12"
        )
        UUID produtoId,
        @Schema(
            description = "Nome do produto interno do combo",
            example = "X-Bacon"
        )
        String nomeProduto,
        @Schema(
            description = "Quantidade do produto interno do combo",
            example = "2"
        )
        Integer quantidade,
        @Schema(
            description = "Preço unitário do produto interno do combo no momento da venda",
            example = "15.00"
        )
        BigDecimal precoUnitario,
        @Schema(description = "Lista de adicionais aplicados a este item de combo")
        List<AdicionalInfo> adicionais,
        @Schema(
            description = "Observação individual para o item de combo",
            example = "Sem cebola"
        )
        String observacao
) {
    public ItemComboResponseDTO(ItemCombo entity) {
        this(
                entity.getId(),
                entity.getItemPedido() != null ? entity.getItemPedido().getId() : null,
                entity.getProdutoId(),
                entity.getNomeProduto(),
                entity.getQuantidade(),
                entity.getPrecoUnitario(),
                entity.getAdicionais() != null ? entity.getAdicionais().stream()
                        .map(a -> new AdicionalInfo(a.getId(), a.getNome(), a.getPreco()))
                        .collect(Collectors.toList()) : List.of(),
                entity.getObservacao()
        );
    }
}