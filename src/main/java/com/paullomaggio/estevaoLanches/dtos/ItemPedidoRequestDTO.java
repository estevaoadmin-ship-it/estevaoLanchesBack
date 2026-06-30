package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * DTO para validação e entrada de dados de itens no momento do lançamento do pedido.
 */
@Schema(description = "DTO para adicionar ou atualizar um item em um pedido")
public record ItemPedidoRequestDTO(
        @Schema(
            description = "ID do produto",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O ID do produto é obrigatório.")
        UUID produtoId,

        @Schema(
            description = "Quantidade do produto",
            example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "A quantidade é obrigatória.")
        @Min(value = 1, message = "A quantidade mínima deve ser 1.")
        Integer quantidade,

        @Schema(
            description = "Observações específicas para este item",
            example = "Sem picles"
        )
        String observacao,

        @Schema(
            description = "Lista de IDs dos adicionais para este item",
            type = "array",
            example = "[\"1a2b3c4d-5e6f-7890-1234-567890abcdef\", \"fedcba98-7654-3210-fedc-ba9876543210\"]"
        )
        List<UUID> adicionaisIds,

        @Schema(
            description = "Número da conta (para pedidos de mesa com múltiplas contas)",
            example = "1"
        )
        Integer numeroConta
) {
        /**
         * Construtor compacto para garantir que nenhum item fique sem vínculo de conta.
         */
        public ItemPedidoRequestDTO {
                if (numeroConta == null) {
                        numeroConta = 1;
                }
        }
}