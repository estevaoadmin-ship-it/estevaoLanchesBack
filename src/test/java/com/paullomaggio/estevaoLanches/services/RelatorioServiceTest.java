package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.DashboardDataDTO;
import com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoRankingDTO;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelatorioService - dashboard gerencial e PDF")
class RelatorioServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @InjectMocks private RelatorioService relatorioService;

    private LocalDateTime inicio;
    private LocalDateTime fim;
    private List<Pedido> pedidosPeriodo;
    private List<MeioPagamentoItemDTO> meiosPagamento;
    private List<ProdutoRankingDTO> topProdutos;

    @BeforeEach
    void setUp() {
        inicio = LocalDateTime.of(2026, 1, 1, 0, 0);
        fim = LocalDateTime.of(2026, 1, 31, 23, 59);

        pedidosPeriodo = List.of(
                pedido(new BigDecimal("100.00"), StatusPedido.FINALIZADO, TipoPedido.MESA, FormaPagamento.PIX),
                pedido(new BigDecimal("50.00"), StatusPedido.FINALIZADO, TipoPedido.DELIVERY, FormaPagamento.DINHEIRO),
                pedido(new BigDecimal("30.00"), StatusPedido.CANCELADO, TipoPedido.BALCAO, FormaPagamento.CREDITO),
                pedido(new BigDecimal("40.00"), StatusPedido.RECEBIDO, TipoPedido.MESA, null)
        );

        meiosPagamento = List.of(
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("100.00")),
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("50.00"))
        );

        topProdutos = List.of(
                new ProdutoRankingDTO("X-Bacon", 15L),
                new ProdutoRankingDTO("Coca-Cola", 10L)
        );
    }

    @Nested
    @DisplayName("1 e 4. Dashboard e KPIs")
    class DashboardEKpisTests {

        @Test
        @DisplayName("CT-REL-001 ao CT-REL-008, CT-REL-022 ao CT-REL-026 - Calcula KPIs com pedidos finalizados e cancelados")
        void deveGerarDashboardComMatematicaCorreta() {
            mockarDashboardBasico(pedidosPeriodo, meiosPagamento, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin-123");

            assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo("150.00");
            assertThat(resultado.getKpis().getTotalPedidos()).isEqualTo(2);
            assertThat(resultado.getKpis().getTicketMedio()).isEqualByComparingTo("75.00");
            assertThat(resultado.getKpis().getTotalCancelamentos()).isEqualTo(1);
            assertThat(resultado.getKpis().getPerdaCancelamentos()).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("CT-REL-027 - Periodo sem vendas mantem KPIs zerados")
        void ctRel027_kpisZeradosSemVendas() {
            mockarDashboardBasico(List.of(), List.of(), List.of());

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, null);

            assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resultado.getKpis().getTotalPedidos()).isZero();
            assertThat(resultado.getKpis().getTicketMedio()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resultado.getKpis().getTotalCancelamentos()).isZero();
        }
    }

    @Nested
    @DisplayName("2. Periodos")
    class PeriodoRelatoriosTests {

        @Test
        @DisplayName("CT-REL-009 ao CT-REL-015 - Consulta pedidos exatamente dentro da janela informada")
        void ctRel009_deveProcessarPeriodosValidos() {
            mockarDashboardBasico(pedidosPeriodo, meiosPagamento, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertNotNull(resultado);
            verify(pedidoRepository).buscarPedidosParaRelatorio(inicio, fim);
            verify(pedidoRepository).somarFaturamentoPorMeioPagamento(inicio, fim, StatusPedido.FINALIZADO);
        }
    }

    @Nested
    @DisplayName("3. Compatibilidade do usuarioId")
    class OperadorSanitizacaoTests {

        @Test
        @DisplayName("CT-REL-017 ao CT-REL-021 - usuarioId permanece compativel, mas nao altera a query de pedidos")
        void deveManterUsuarioIdForaDaQueryDaNovaArquitetura() {
            mockarDashboardBasico(pedidosPeriodo, meiosPagamento, topProdutos);

            relatorioService.gerarDashboard(inicio, fim, "operador-que-nao-existe-na-tabela-pedido");

            verify(pedidoRepository).buscarPedidosParaRelatorio(inicio, fim);
            verifyNoMoreInteractionsAposAgregacoes();
        }
    }

    @Nested
    @DisplayName("5. Matematica financeira")
    class MatematicaFinanceiraTests {

        @Test
        @DisplayName("CT-REL-030 e CT-REL-088 - Sem pedidos finalizados, ticket medio fica zero")
        void deveLidarComPeriodoSemVendas() {
            List<Pedido> apenasNaoFinalizados = List.of(
                    pedido(new BigDecimal("30.00"), StatusPedido.CANCELADO, TipoPedido.DELIVERY, FormaPagamento.PIX),
                    pedido(new BigDecimal("20.00"), StatusPedido.RECEBIDO, TipoPedido.MESA, null)
            );
            mockarDashboardBasico(apenasNaoFinalizados, List.of(), List.of());

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, null);

            assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resultado.getKpis().getTicketMedio()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(resultado.getKpis().getPerdaCancelamentos()).isEqualByComparingTo("30.00");
        }
    }

    @Nested
    @DisplayName("6 e 7. Meios de pagamento e ranking")
    class MeiosPagamentoERankingTests {

        @Test
        @DisplayName("CT-REL-034 ao CT-REL-046 - Usa agregacoes de finalizados e limita top produtos em 5")
        void ctRel034_deveValidarEstruturasDeMapeamento() {
            mockarDashboardBasico(pedidosPeriodo, meiosPagamento, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertThat(resultado.getMeiosPagamento()).containsExactlyElementsOf(meiosPagamento);
            assertThat(resultado.getTopProdutos().get(0).getNomeProduto()).isEqualTo("X-Bacon");

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(pedidoRepository).buscarTopProdutosJPQL(eq(inicio), eq(fim), eq(StatusPedido.FINALIZADO), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("8. Exportacao PDF")
    class ExportacaoPdfTests {

        @Test
        @DisplayName("CT-REL-047 ao CT-REL-052, CT-REL-083 - Exporta PDF com assinatura valida")
        void deveExportarRelatorioPdfComAssinaturaValida() {
            mockarDashboardBasico(pedidosPeriodo, meiosPagamento, topProdutos);

            byte[] pdfBytes = relatorioService.exportarRelatorioPdf(inicio, fim, null);

            assertThat(pdfBytes).isNotEmpty();
            assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("CT-REL-053 e CT-REL-054 - Falha do banco na exportacao vira RuntimeException")
        void deveLancarExcecaoAoFalharGeracaoPdf() {
            when(pedidoRepository.buscarPedidosParaRelatorio(any(), any())).thenThrow(new RuntimeException("Banco falhou"));

            assertThrows(RuntimeException.class, () -> relatorioService.exportarRelatorioPdf(inicio, fim, null));
        }
    }

    @Nested
    @DisplayName("9 e 10. Status operacionais")
    class RepositorioEStatusTests {

        @Test
        @DisplayName("CT-REL-055 ao CT-REL-064 - Apenas FINALIZADO entra no faturamento")
        void ctRel060_somenteFinalizadosEntramNoFaturamento() {
            mockarDashboardBasico(pedidosPeriodo, meiosPagamento, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo("150.00");
            assertThat(resultado.getKpis().getPerdaCancelamentos()).isEqualByComparingTo("30.00");
        }
    }

    @Nested
    @DisplayName("11 a 15. Multicanal")
    class AuditoriaEstevaoLanchesTests {

        @Test
        @DisplayName("CT-REL-078, CT-REL-091 ao CT-REL-094 - Consolida mesa, delivery e balcao no mesmo dashboard")
        void ctRel094_fluxoMistoCanaisVenda() {
            mockarDashboardBasico(pedidosPeriodo, meiosPagamento, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertThat(resultado.getKpis().getTotalPedidos()).isEqualTo(2);
            assertThat(resultado.getTopProdutos()).hasSize(2);
            verify(pedidoRepository).buscarPedidosParaRelatorio(inicio, fim);
        }
    }

    private Pedido pedido(BigDecimal total, StatusPedido status, TipoPedido tipo, FormaPagamento formaPagamento) {
        Pedido pedido = new Pedido();
        pedido.setTotal(total);
        pedido.setStatus(status);
        pedido.setTipo(tipo);
        pedido.setFormaPagamento(formaPagamento);
        pedido.setDataHora(inicio.plusHours(1));
        return pedido;
    }

    private void mockarDashboardBasico(
            List<Pedido> pedidos,
            List<MeioPagamentoItemDTO> pagamentos,
            List<ProdutoRankingDTO> produtos
    ) {
        when(pedidoRepository.buscarPedidosParaRelatorio(inicio, fim)).thenReturn(pedidos);
        when(pedidoRepository.somarFaturamentoPorMeioPagamento(inicio, fim, StatusPedido.FINALIZADO)).thenReturn(pagamentos);
        when(pedidoRepository.buscarTopProdutosJPQL(eq(inicio), eq(fim), eq(StatusPedido.FINALIZADO), any(Pageable.class))).thenReturn(produtos);
    }

    private void verifyNoMoreInteractionsAposAgregacoes() {
        verify(pedidoRepository).somarFaturamentoPorMeioPagamento(inicio, fim, StatusPedido.FINALIZADO);
        verify(pedidoRepository).buscarTopProdutosJPQL(eq(inicio), eq(fim), eq(StatusPedido.FINALIZADO), any(Pageable.class));
        verifyNoMoreInteractions(pedidoRepository);
    }
}
