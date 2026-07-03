package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "DTO exclusivo para checkout de novos pedidos realizados diretamente no Balcão")
public record CheckoutBalcaoRequestDTO(
        @Schema(description = "Nome do consumidor imediato no balcão", example = "Mariana")
        String nomeConsumidor,

        @Schema(description = "Forma de pagamento selecionada", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaPagamento formaPagamento,

        @Schema(description = "Observações gerais sobre a preparação do pedido", example = "Sem maionese")
        String observacao,

        @Schema(description = "Lista de itens a serem produzidos", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "O pedido de balcão deve conter pelo menos um item")
        @Valid
        List<ItemPedidoRequestDTO> itens
) {}