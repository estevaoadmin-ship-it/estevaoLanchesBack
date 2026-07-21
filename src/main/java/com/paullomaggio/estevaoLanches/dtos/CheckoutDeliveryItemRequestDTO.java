package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Schema(description = "DTO para um item de pedido em um checkout de Delivery, usado quando os itens são enviados explicitamente pelo PDV.")
public record CheckoutDeliveryItemRequestDTO(
        @Schema(description = "ID do produto", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID produtoId,

        @Schema(description = "Quantidade do produto", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(1)
        Integer quantidade,

        @Schema(description = "Observações específicas para este item", example = "Sem cebola")
        String observacao,

        @Schema(description = "Lista de IDs de adicionais aplicados a este item", format = "uuid")
        List<UUID> adicionaisIds,

        @Schema(description = "Lista de customizações de itens de combo, se o produto for um combo")
        List<ItemComboCustomizacaoRequestDTO> itensCombo
) {}
