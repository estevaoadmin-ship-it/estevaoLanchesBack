package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import jakarta.validation.constraints.NotNull;

public record PedidoStatusRequestDTO(
        @NotNull(message = "O novo status é obrigatório")
        StatusPedido status
) {}