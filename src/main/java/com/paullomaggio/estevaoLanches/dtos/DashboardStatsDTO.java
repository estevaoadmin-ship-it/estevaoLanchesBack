package com.paullomaggio.estevaoLanches.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    private BigDecimal faturamentoTotal;
    private Long totalPedidos;
    private BigDecimal ticketMedio;
    private Long totalCancelamentos;
    private BigDecimal perdaCancelamentos;
}