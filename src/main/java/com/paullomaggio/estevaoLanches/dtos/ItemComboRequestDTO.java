package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ItemComboRequestDTO(
        @NotNull(message = "O vínculo com o item do pedido é obrigatório.")
        UUID itemPedidoId,

        @NotNull(message = "O ID do produto original é obrigatório.")
        UUID produtoId,

        @NotBlank(message = "O nome do produto não pode ficar em branco.")
        String nomeProduto,

        @NotNull(message = "A quantidade é obrigatória.")
        @Min(value = 1, message = "A quantidade de saídas deve ser de no mínimo 1.")
        Integer quantidade,

        @NotNull(message = "O preço unitário histórico do item é obrigatório.")
        BigDecimal precoUnitario
) {
    public ItemComboRequestDTO {
        if (nomeProduto != null) nomeProduto = nomeProduto.trim().toUpperCase();
    }
}