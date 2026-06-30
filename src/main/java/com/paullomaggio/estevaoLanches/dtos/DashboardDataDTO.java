package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO contendo todos os dados para o dashboard gerencial")
public class DashboardDataDTO {
    @Schema(description = "Principais indicadores de performance (KPIs) do dashboard")
    private DashboardStatsDTO kpis;
    @Schema(description = "Lista de faturamento por meio de pagamento")
    private List<MeioPagamentoItemDTO> meiosPagamento;
    @Schema(description = "Ranking dos produtos mais vendidos")
    private List<ProdutoRankingDTO> topProdutos;
}