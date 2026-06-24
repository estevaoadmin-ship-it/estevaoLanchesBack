package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * DTO para validação de entrada de dados de criação e atualização de mesas.
 */
public record MesaRequestDTO(
        @NotNull(message = "O número da mesa é obrigatório.")
        @Positive(message = "O número da mesa deve ser um valor positivo.")
        Integer numero,

        @NotNull(message = "O status inicial da mesa é obrigatório.")
        StatusMesa status,

        UUID empresaId,
        UUID filialId
) {}