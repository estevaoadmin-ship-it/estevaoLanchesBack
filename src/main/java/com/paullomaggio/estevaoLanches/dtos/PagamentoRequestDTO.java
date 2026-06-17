package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PagamentoRequestDTO(
        @NotNull(message = "A forma de pagamento é obrigatória.")
        FormaPagamento formaPagamento,

        BigDecimal valorRecebido
) {}