package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO contendo as estatísticas e KPIs do dashboard gerencial")
public class DashboardStatsDTO {
    @Schema(description = "Faturamento total no período", example = "1500.75")
    private BigDecimal faturamentoTotal;
    @Schema(description = "Número total de pedidos no período", example = "120")
    private Long totalPedidos;
    @Schema(description = "Valor médio por pedido (ticket médio)", example = "12.50")
    private BigDecimal ticketMedio;
    @Schema(description = "Número total de pedidos cancelados no período", example = "5")
    private Long totalCancelamentos;
    @Schema(description = "Valor total de perdas devido a cancelamentos", example = "75.20")
    private BigDecimal perdaCancelamentos;
}