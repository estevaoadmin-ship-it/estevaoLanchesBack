package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.MotivoMovimentacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record MovimentacaoRequestDTO(
        @NotNull(message = "O valor da movimentação é obrigatório.")
        @Positive(message = "O valor deve ser maior que zero.")
        BigDecimal valor,

        @NotNull(message = "O motivo padronizado é obrigatório.")
        MotivoMovimentacao motivo,

        String observacao
) {}