package com.paullomaggio.estevaoLanches.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDataDTO {
    private DashboardStatsDTO kpis;
    private List<MeioPagamentoItemDTO> meiosPagamento;
    private List<ProdutoRankingDTO> topProdutos;
}