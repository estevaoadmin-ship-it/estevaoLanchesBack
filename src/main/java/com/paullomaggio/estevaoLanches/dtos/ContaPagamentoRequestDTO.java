package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ContaPagamentoRequestDTO(
        @NotNull(message = "O número da subcomanda/conta é obrigatório.")
        Integer numeroConta,

        @NotNull(message = "O valor recebido é obrigatório.")
        @Positive(message = "O valor recebido deve ser maior que zero.")
        BigDecimal valorRecebido,

        @NotNull(message = "A forma de pagamento é obrigatória.")
        FormaPagamento formaPagamento
) {}