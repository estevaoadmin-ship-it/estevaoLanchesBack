package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ContaRequestDTO(
        @NotNull(message = "O número identificador da subconta é obrigatório.")
        @Positive(message = "O número da conta deve ser maior que zero.")
        Integer numeroConta,

        @NotNull(message = "O ID da comanda mestre é obrigatório.")
        UUID comandaId
) {}