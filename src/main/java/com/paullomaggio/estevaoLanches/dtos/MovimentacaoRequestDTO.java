package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record MovimentacaoRequestDTO(
        @NotNull(message = "O valor da movimentação é obrigatório.")
        @Positive(message = "O valor da movimentação deve ser maior que zero.")
        BigDecimal valor,

        @NotBlank(message = "A descrição/justificativa da movimentação é obrigatória.")
        String descricao
) {}