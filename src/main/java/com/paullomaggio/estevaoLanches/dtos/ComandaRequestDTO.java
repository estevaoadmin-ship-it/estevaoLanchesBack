package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * DTO para validação do payload de abertura ou manipulação de Comandas.
 * Permite a abertura simples apenas por mesa ou com identificação imediata do cliente.
 */
public record ComandaRequestDTO(
        @NotNull(message = "O número da mesa é obrigatório.")
        @Positive(message = "O número da mesa deve ser um valor positivo.")
        Integer numeroMesa,

        UUID empresaId,
        UUID filialId,

        // Dados opcionais do cliente para abertura imediata com identificação
        String clienteNome,
        String clienteTelefone
) {}