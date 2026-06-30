package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "DTO para atualização do status de um pedido")
public record PedidoStatusRequestDTO(
        @Schema(
            description = "Novo status do pedido",
            allowableValues = {
                "PENDENTE",
                "EM_PREPARO",
                "PRONTO",
                "FINALIZADO",
                "CANCELADO"
            },
            example = "EM_PREPARO",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O novo status é obrigatório")
        StatusPedido status
) {}