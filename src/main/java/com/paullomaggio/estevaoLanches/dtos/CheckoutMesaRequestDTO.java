package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.UUID;

public record CheckoutMesaRequestDTO(
        @NotNull UUID comandaId,
        @NotNull UUID contaId,
        @NotNull UUID garcomId,
        String observacao
) {}