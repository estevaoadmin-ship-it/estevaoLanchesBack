package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "DTO exclusivo para pedidos feitos pelo App com retirada presencial")
public record CheckoutRetiradaRequestDTO(
        @Schema(description = "ID do cliente persistido no banco de dados", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O ID do cliente cadastrado é obrigatório")
        UUID clienteId,

        @Schema(description = "Forma de pagamento pré-definida ou escolhida", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaPagamento formaPagamento,

        @Schema(description = "Observações ou horário previsto para a busca do lanche", example = "Vou retirar em 20 minutos")
        String observacao
) {}