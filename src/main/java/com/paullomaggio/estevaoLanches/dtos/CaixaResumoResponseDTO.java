package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Resumo financeiro do caixa para o período atual")
public record CaixaResumoResponseDTO(
        @Schema(
            description = "Faturamento total do caixa no período",
            example = "500.00"
        )
        BigDecimal faturamentoTotal,
        @Schema(
            description = "Faturamento via dinheiro no período",
            example = "150.00"
        )
        BigDecimal faturamentoDinheiro,
        @Schema(
            description = "Faturamento via PIX no período",
            example = "200.00"
        )
        BigDecimal faturamentoPix,
        @Schema(
            description = "Faturamento via cartão de crédito no período",
            example = "100.00"
        )
        BigDecimal faturamentoCredito,
        @Schema(
            description = "Faturamento via cartão de débito no período",
            example = "50.00"
        )
        BigDecimal faturamentoDebito,
        @Schema(
            description = "Total esperado na gaveta do caixa (valor de abertura + faturamento em dinheiro)",
            example = "250.00"
        )
        BigDecimal totalEsperadoGaveta,
        @Schema(
            description = "Número de pedidos atualmente em preparo na esteira",
            example = "3"
        )
        long pedidosEmEsteira
) {}