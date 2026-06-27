package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckoutBalcaoRequestDTO(
        @NotNull UUID clienteId,
        String nomeConsumidor,
        @NotNull FormaPagamento formaPagamento,
        String observacao
) {}