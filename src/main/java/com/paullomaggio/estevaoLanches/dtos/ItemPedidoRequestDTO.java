package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * DTO para validação e entrada de dados de itens no momento do lançamento do pedido.
 */
public record ItemPedidoRequestDTO(
        @NotNull(message = "O ID do produto é obrigatório.")
        UUID produtoId,

        @NotNull(message = "A quantidade é obrigatória.")
        @Min(value = 1, message = "A quantidade mínima deve ser 1.")
        Integer quantidade,

        String observacao,

        List<UUID> adicionaisIds,

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