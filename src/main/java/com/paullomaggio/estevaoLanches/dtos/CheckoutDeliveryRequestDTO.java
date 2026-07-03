package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "DTO exclusivo para checkout de pedidos via Delivery (com cliente cadastrado)")
public record CheckoutDeliveryRequestDTO(
        @Schema(description = "ID do cliente persistido no banco de dados", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O ID do cliente cadastrado é obrigatório")
        UUID clienteId,

        @Schema(description = "Endereço completo para entrega do motoboy", example = "Av. Paulista, 1000 - Apto 42", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O endereço de entrega é obrigatório")
        String enderecoEntrega,

        @Schema(description = "Forma de pagamento escolhida para o recebimento", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaPagamento formaPagamento,

        @Schema(description = "Observações e pontos de referência para a entrega", example = "Próximo ao metrô")
        String observacao
) {}