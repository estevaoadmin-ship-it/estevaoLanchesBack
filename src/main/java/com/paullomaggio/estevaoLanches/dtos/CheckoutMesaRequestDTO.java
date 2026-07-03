package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "DTO exclusivo para o fechamento financeiro de uma Mesa")
public record CheckoutMesaRequestDTO(
        @Schema(description = "Identificador único da Comanda Mestre", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O ID da comanda mestre é obrigatório")
        UUID comandaId,

        @Schema(description = "Número da subconta/partição da mesa", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O número da conta é obrigatório")
        Integer numeroConta,

        @Schema(description = "Nome do cliente responsável pelo pagamento da conta", example = "Carlos Silva", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O nome do responsável é obrigatório")
        String nomeResponsavel,

        @Schema(description = "Telefone opcional de contato do responsável", example = "11988887777")
        String telefoneResponsavel,

        @Schema(description = "Forma de pagamento escolhida para liquidação", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaPagamento formaPagamento
) {}