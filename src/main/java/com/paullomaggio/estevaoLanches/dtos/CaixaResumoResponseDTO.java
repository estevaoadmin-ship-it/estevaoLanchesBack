package com.paullomaggio.estevaoLanches.dtos;

import java.math.BigDecimal;

public record CaixaResumoResponseDTO(
        BigDecimal faturamentoTotal,
        BigDecimal faturamentoDinheiro,
        BigDecimal faturamentoPix,
        BigDecimal faturamentoCredito,
        BigDecimal faturamentoDebito,
        BigDecimal totalEsperadoGaveta,
        long pedidosEmEsteira
) {}