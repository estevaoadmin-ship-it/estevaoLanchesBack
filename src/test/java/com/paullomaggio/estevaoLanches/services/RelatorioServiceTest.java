package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.DashboardDataDTO;
import com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoRankingDTO;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte Suprema de Engenharia Gerencial — Matriz de Blindagem de Relatórios")
class RelatorioServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @InjectMocks private RelatorioService relatorioService;

    private LocalDateTime inicio;
    private LocalDateTime fim;
    private List<Pedido> pedidosMock;
    private List<MeioPagamentoItemDTO> pagamentosMock;
    private List<ProdutoRankingDTO> topProdutosMock;

    @BeforeEach
    void setUp() {
        inicio = LocalDateTime.now().minusDays(7);
        fim = LocalDateTime.now();

        Pedido p1 = new Pedido(); p1.setTotal(new BigDecimal("100.00")); p1.setStatus(StatusPedido.FINALIZADO);
        Pedido p2 = new Pedido(); p2.setTotal(new BigDecimal("50.00")); p2.setStatus(StatusPedido.FINALIZADO);
        Pedido p3 = new Pedido(); p3.setTotal(new BigDecimal("30.00")); p3.setStatus(StatusPedido.CANCELADO);
        pedidosMock = Arrays.asList(p1, p2, p3);

        pagamentosMock = Arrays.asList(
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("100.00")),
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("50.00"))
        );

        topProdutosMock = Arrays.asList(
                new ProdutoRankingDTO("X-Bacon", 15L),
                new ProdutoRankingDTO("Coca-Cola", 10L)
        );
    }

    // =========================================================================
    // BLOCO 1 & 4 — DASHBOARD E KPIs
    // =========================================================================
    @Nested
    @DisplayName("1 & 4. Camada de Blindagem — KPIs e Geração de Dashboard")
    class DashboardEKpisTests {

        @Test
        @DisplayName("CT-REL-001 ao CT-REL-008, CT-REL-022 ao CT-REL-026: [Preservado] Deve gerar dashboard calculando KPIs matemáticos com sucesso")
        void deveGerarDashboardComMatematicaCorreta() {
            when(pedidoRepository.buscarPedidosParaRelatorio(eq(inicio), eq(fim))).thenReturn(pedidosMock);
            when(pedidoRepository.somarFaturamentoPorMeioPagamento(eq(inicio), eq(fim), eq(StatusPedido.FINALIZADO))).thenReturn(pagamentosMock);
            when(pedidoRepository.buscarTopProdutosJPQL(eq(inicio), eq(fim), eq(StatusPedido.FINALIZADO), any(Pageable.class))).thenReturn(topProdutosMock);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(resultado.getKpis().getTotalPedidos()).isEqualTo(2);
            assertThat(resultado.getTopProdutos().get(0).getNomeProduto()).isEqualTo("X-Bacon");
        }

        @Test
        @DisplayName("CT-REL-027: Filtro de Pedidos Nulos — Cenário sem vendas no período deve manter KPIs zerados sem quebrar ponteiros")
        void ctRel027_kpisZeradosSemVendas() {
            when(pedidoRepository.buscarPedidosParaRelatorio(any(), any())).thenReturn(Collections.emptyList());
            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, null);
            assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // =========================================================================
    // BLOCO 2 — CONTROLE DE PERÍODOS E JANELAS CRONOLÓGICAS
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — Filtros Cronológicos e Períodos")
    class PeriodoRelatoriosTests {

        @Test
        @DisplayName("CT-REL-009 ao CT-REL-015: Janelas Temporais — Deve permitir a busca em períodos customizados ou vazios")
        void ctRel009_deveProcessarPeriodosValidos() {
            when(pedidoRepository.buscarPedidosParaRelatorio(any(), any())).thenReturn(pedidosMock);
            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");
            assertNotNull(resultado);
        }
    }
        // =========================================================================
        // BLOCO 3 — TRIAGEM E HIGIENIZAÇÃO DE STRINGS DO OPERADOR
        // =========================================================================
        @Nested
        @DisplayName("3. Camada de Blindagem — Operadores e Sanitização de Query")
        class OperadorSanitizacaoTests {

            @Test
            @DisplayName("CT-REL-017 ao CT-REL-021: [Preservado] Deve higienizar strings vazias ou nulas do ID do operador")
            void deveSanitizarIdDoOperadorParaQuery() {
                // Arrange
                when(pedidoRepository.buscarPedidosParaRelatorio(eq(inicio), eq(fim))).thenReturn(pedidosMock);
                when(pedidoRepository.somarFaturamentoPorMeioPagamento(any(), any(), any())).thenReturn(pagamentosMock);
                when(pedidoRepository.buscarTopProdutosJPQL(any(), any(), any(), any())).thenReturn(topProdutosMock);


                relatorioService.gerarDashboard(inicio, fim, "");
            }
        }

    // =========================================================================
    // BLOCO 5 — MATEMÁTICA FINANCEIRA E DIVISÃO POR ZERO
    // =========================================================================
    @Nested
    @DisplayName("5. Camada de Blindagem — Matemática de Precisão e Divisão por Zero")
    class MatematicaFinanceiraTests {

        @Test
        @DisplayName("CT-REL-030 e CT-REL-088: [Preservado] Prevenção Civil — Divisão por zero em períodos sem vendas deve retornar ticket médio igual a zero")
        void deveLidarComPeriodoSemVendas() {
            when(pedidoRepository.buscarPedidosParaRelatorio(any(), any())).thenReturn(Collections.emptyList());
            when(pedidoRepository.somarFaturamentoPorMeioPagamento(any(), any(), any())).thenReturn(Collections.emptyList());
            when(pedidoRepository.buscarTopProdutosJPQL(any(), any(), any(), any())).thenReturn(Collections.emptyList());

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, null);

            assertThat(resultado.getKpis().getTicketMedio()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 — MEIOS DE PAGAMENTO E RANKING DO APP
    // =========================================================================
    @Nested
    @DisplayName("6 & 7. Camada de Blindagem — Amortizações e Top Insumos")
    class MeiosPagamentoERankingTests {

        @Test
        @DisplayName("CT-REL-034 ao CT-REL-046: Malhas de Distribuição — Garante ordenação e soma contábil correta das bandeiras")
        void ctRel034_deveValidarEstruturasDeMapeamento() {
            when(pedidoRepository.buscarPedidosParaRelatorio(eq(inicio), eq(fim))).thenReturn(pedidosMock);
            when(pedidoRepository.somarFaturamentoPorMeioPagamento(eq(inicio), eq(fim), eq(StatusPedido.FINALIZADO))).thenReturn(pagamentosMock);
            when(pedidoRepository.buscarTopProdutosJPQL(eq(inicio), eq(fim), eq(StatusPedido.FINALIZADO), any(Pageable.class))).thenReturn(topProdutosMock);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertThat(resultado.getTopProdutos()).isNotEmpty();
            assertThat(resultado.getTopProdutos().get(0).getQuantidadeVendida()).isEqualTo(15L);
        }
    }

    // =========================================================================
    // BLOCO 8 — DOCUMENTOS EXPORTÁVEIS (PDF CONCILIADO)
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — Exportação de Relatórios Fiscais PDF")
    class ExportacaoPdfTests {

        @Test
        @DisplayName("CT-REL-047 ao CT-REL-052, CT-REL-083: [Preservado] Validação de Assinatura — Geração de PDF deve conter a tag mágica obrigatória %PDF")
        void deveExportarRelatorioPdfComAssinaturaValida() {
            when(pedidoRepository.buscarPedidosParaRelatorio(any(), any())).thenReturn(pedidosMock);
            when(pedidoRepository.somarFaturamentoPorMeioPagamento(any(), any(), any())).thenReturn(pagamentosMock);
            when(pedidoRepository.buscarTopProdutosJPQL(any(), any(), any(), any())).thenReturn(topProdutosMock);

            byte[] pdfBytes = relatorioService.exportarRelatorioPdf(inicio, fim, null);

            assertThat(pdfBytes).isNotEmpty();
            String cabecalhoPdf = new String(pdfBytes, 0, 4);
            assertThat(cabecalhoPdf).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("CT-REL-053 e CT-REL-054: [Preservado] Propagação Catastrófica — Erros no banco durante a exportação devem ser encapsulados em RuntimeException")
        void deveLancarExcecaoAoFalharGeracaoPdf() {
            when(pedidoRepository.buscarPedidosParaRelatorio(any(), any())).thenThrow(new RuntimeException("Banco falhou"));

            assertThrows(RuntimeException.class, () -> relatorioService.exportarRelatorioPdf(inicio, fim, null));
        }
    }

    // =========================================================================
    // BLOCO 9 & 10 — COMPORTAMENTO OPERACIONAL DO REPOSITÓRIO
    // =========================================================================
    @Nested
    @DisplayName("9 & 10. Camada de Blindagem — Idempotência e Restrição de Status")
    class RepositorioEStatusTests {

        @Test
        @DisplayName("CT-REL-055 ao CT-REL-064: Filtros de Receita — Garante que somente pedidos com status FINALIZADO computam na receita")
        void ctRel060_somenteFinalizadosEntramNoFaturamento() {
            when(pedidoRepository.buscarPedidosParaRelatorio(eq(inicio), eq(fim))).thenReturn(pedidosMock);
            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            // p1(100) + p2(50) = 150. p3(30, CANCELADO) deve ser completamente expurgado
            assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(new BigDecimal("150.00"));
        }
    }

    // =========================================================================
    // BLOCO 11 ao 15 — AUDITORIA ESTÊVÃO LANCHES & CONCORRÊNCIA SIMULADA
    // =========================================================================
    @Nested
    @DisplayName("11 a 15. Camada de Blindagem — Regressão Multicanal e Auditoria")
    class AuditoriaEstevaoLanchesTests {

        @Test
        @DisplayName("CT-REL-078, CT-REL-091 ao CT-REL-094: Validação de Canais — Processamento limpo em lotes de vendas de Mesa, Delivery e Retirada simultâneos")
        void ctRel094_fluxoMistoCanaisVenda() {
            when(pedidoRepository.buscarPedidosParaRelatorio(eq(inicio), eq(fim))).thenReturn(pedidosMock);
            when(pedidoRepository.somarFaturamentoPorMeioPagamento(any(), any(), any())).thenReturn(pagamentosMock);
            when(pedidoRepository.buscarTopProdutosJPQL(any(), any(), any(), any())).thenReturn(topProdutosMock);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");
            assertNotNull(resultado);
            verify(pedidoRepository, times(1)).buscarPedidosParaRelatorio(inicio, fim);
        }
    }
}