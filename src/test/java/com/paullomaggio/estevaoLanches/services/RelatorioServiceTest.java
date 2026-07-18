package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.DashboardDataDTO;
import com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoRankingDTO;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.repositories.EstornoPagamentoRepository;
import com.paullomaggio.estevaoLanches.repositories.PagamentoRepository;
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
import java.util.Collections;
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
    @Mock private PagamentoRepository pagamentoRepository;
    @Mock private EstornoPagamentoRepository estornoPagamentoRepository; // Novo mock
    @InjectMocks private RelatorioService relatorioService;

    private LocalDateTime inicio;
    private LocalDateTime fim;
    private List<Pedido> pedidosPeriodo;
    private List<MeioPagamentoItemDTO> pagamentosBrutos;
    private List<MeioPagamentoItemDTO> estornosPorForma;
    private List<ProdutoRankingDTO> topProdutos;
    private BigDecimal faturamentoBrutoEsperado;
    private BigDecimal totalEstornosEsperado;

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

        faturamentoBrutoEsperado = new BigDecimal("150.00"); // 100 PIX + 50 DINHEIRO
        totalEstornosEsperado = BigDecimal.ZERO; // Default para a maioria dos testes existentes

        pagamentosBrutos = List.of(
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("100.00")),
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("50.00"))
        );

        estornosPorForma = Collections.emptyList(); // Default

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
            mockarDashboardBasico(pedidosPeriodo, faturamentoBrutoEsperado, totalEstornosEsperado, pagamentosBrutos, estornosPorForma, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin-123");

            // Faturamento total agora é líquido
            assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoBrutoEsperado.subtract(totalEstornosEsperado));
            // Total de pedidos e ticket médio continuam vindo do PedidoRepository (métricas comerciais)
            assertThat(resultado.getKpis().getTotalPedidos()).isEqualTo(2);
            assertThat(resultado.getKpis().getTicketMedio()).isEqualByComparingTo("75.00");
            assertThat(resultado.getKpis().getTotalCancelamentos()).isEqualTo(1);
            assertThat(resultado.getKpis().getPerdaCancelamentos()).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("CT-REL-027 - Periodo sem vendas mantem KPIs zerados")
        void ctRel027_kpisZeradosSemVendas() {
            mockarDashboardBasico(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of());

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
            mockarDashboardBasico(pedidosPeriodo, faturamentoBrutoEsperado, totalEstornosEsperado, pagamentosBrutos, estornosPorForma, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertNotNull(resultado);
            verify(pedidoRepository).buscarPedidosParaRelatorio(inicio, fim);
            verify(pagamentoRepository).somarFaturamentoBrutoPorPeriodo(inicio, fim);
            verify(pagamentoRepository).somarFaturamentoPorMeioPagamentoPorPeriodo(inicio, fim);
            verify(estornoPagamentoRepository).somarTotalEstornosPorPeriodo(inicio, fim);
            verify(estornoPagamentoRepository).somarEstornosPorMeioPagamentoPorPeriodo(inicio, fim);
        }
    }

    @Nested
    @DisplayName("3. Compatibilidade do usuarioId")
    class OperadorSanitizacaoTests {

        @Test
        @DisplayName("CT-REL-017 ao CT-REL-021 - usuarioId permanece compativel, mas nao altera a query de pedidos")
        void deveManterUsuarioIdForaDaQueryDaNovaArquitetura() {
            mockarDashboardBasico(pedidosPeriodo, faturamentoBrutoEsperado, totalEstornosEsperado, pagamentosBrutos, estornosPorForma, topProdutos);

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
            mockarDashboardBasico(apenasNaoFinalizados, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of());

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
            mockarDashboardBasico(pedidosPeriodo, faturamentoBrutoEsperado, totalEstornosEsperado, pagamentosBrutos, estornosPorForma, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertThat(resultado.getMeiosPagamento()).containsExactlyElementsOf(pagamentosBrutos); // Still raw here, as refund is zero
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
            mockarDashboardBasico(pedidosPeriodo, faturamentoBrutoEsperado, totalEstornosEsperado, pagamentosBrutos, estornosPorForma, topProdutos);

            byte[] pdfBytes = relatorioService.exportarRelatorioPdf(inicio, fim, null);

            assertThat(pdfBytes).isNotEmpty();
            assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("CT-REL-053 e CT-REL-054 - Falha do banco na exportacao vira RuntimeException")
        void deveLancarExcecaoAoFalharGeracaoPdf() {
            when(pedidoRepository.buscarPedidosParaRelatorio(any(), any()))
                    .thenThrow(new RuntimeException("Banco falhou"));

            assertThrows(
                    RuntimeException.class,
                    () -> relatorioService.exportarRelatorioPdf(inicio, fim, null)
            );
        }
    }

    @Nested
    @DisplayName("9 e 10. Status operacionais")
    class RepositorioEStatusTests {

        @Test
        @DisplayName("CT-REL-055 ao CT-REL-064 - Apenas FINALIZADO entra no faturamento COMERCIAL")
        void ctRel060_somenteFinalizadosEntramNoFaturamentoComercial() {
            mockarDashboardBasico(pedidosPeriodo, faturamentoBrutoEsperado, totalEstornosEsperado, pagamentosBrutos, estornosPorForma, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoBrutoEsperado.subtract(totalEstornosEsperado));
            assertThat(resultado.getKpis().getPerdaCancelamentos()).isEqualByComparingTo("30.00");
        }
    }

    @Nested
    @DisplayName("11 a 15. Multicanal")
    class AuditoriaEstevaoLanchesTests {

        @Test
        @DisplayName("CT-REL-078, CT-REL-091 ao CT-REL-094 - Consolida mesa, delivery e balcao no mesmo dashboard")
        void ctRel094_fluxoMistoCanaisVenda() {
            mockarDashboardBasico(pedidosPeriodo, faturamentoBrutoEsperado, totalEstornosEsperado, pagamentosBrutos, estornosPorForma, topProdutos);

            DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "TODOS");

            assertThat(resultado.getKpis().getTotalPedidos()).isEqualTo(2);
            assertThat(resultado.getTopProdutos()).hasSize(2);
            verify(pedidoRepository).buscarPedidosParaRelatorio(inicio, fim);
        }
    }

    // Teste para o cenário 1: Pedido criado ontem e pago hoje
    @Test
    @DisplayName("CENARIO 1 - Pedido criado ontem e pago hoje: Pagamento deve entrar no dashboard do dia do pagamento")
    void cenario1_pedidoCriadoOntemPagoHoje() {
        LocalDateTime ontem = inicio.minusDays(1);
        LocalDateTime hoje = inicio;

        List<Pedido> pedidosOntem = List.of(
                pedido(new BigDecimal("75.00"), StatusPedido.FINALIZADO, TipoPedido.MESA, FormaPagamento.PIX, ontem.plusHours(22))
        );

        BigDecimal faturamentoHoje = new BigDecimal("75.00");
        List<MeioPagamentoItemDTO> pagamentosHoje = List.of(
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("75.00"))
        );

        // Mock para o dashboard de ONTEM
        mockarDashboardBasico(pedidosOntem, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), Collections.<ProdutoRankingDTO>emptyList(), ontem, ontem.plusDays(1).minusMinutes(1));
        DashboardDataDTO resultadoOntem = relatorioService.gerarDashboard(ontem, ontem.plusDays(1).minusMinutes(1), "admin");
        assertThat(resultadoOntem.getKpis().getFaturamentoTotal()).isEqualByComparingTo(BigDecimal.ZERO);

        // Mock para o dashboard de HOJE
        mockarDashboardBasico(List.of(), faturamentoHoje, BigDecimal.ZERO, pagamentosHoje, List.of(), Collections.<ProdutoRankingDTO>emptyList(), hoje, hoje.plusDays(1).minusMinutes(1));
        DashboardDataDTO resultadoHoje = relatorioService.gerarDashboard(hoje, hoje.plusDays(1).minusMinutes(1), "admin");
        assertThat(resultadoHoje.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoHoje);
        assertThat(resultadoHoje.getMeiosPagamento()).hasSize(1);
        assertThat(resultadoHoje.getMeiosPagamento().get(0).getFormaPagamento()).isEqualTo("PIX");
        assertThat(resultadoHoje.getMeiosPagamento().get(0).getTotalFaturado()).isEqualByComparingTo("75.00");
    }

    // Teste para o cenário 2: Pagamento vinculado à Conta
    @Test
    @DisplayName("CENARIO 2 - Pagamento vinculado à Conta deve entrar no faturamento total e por forma")
    void cenario2_pagamentoContaEntraNoFaturamento() {
        BigDecimal faturamentoConta = new BigDecimal("50.00");
        List<MeioPagamentoItemDTO> pagamentosConta = List.of(
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("50.00"))
        );

        mockarDashboardBasico(List.of(), faturamentoConta, BigDecimal.ZERO, pagamentosConta, List.of(), Collections.<ProdutoRankingDTO>emptyList(), inicio, fim);

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin");

        assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoConta);
        assertThat(resultado.getMeiosPagamento()).hasSize(1);
        assertThat(resultado.getMeiosPagamento().get(0).getFormaPagamento()).isEqualTo("PIX");
        assertThat(resultado.getMeiosPagamento().get(0).getTotalFaturado()).isEqualByComparingTo("50.00");
    }

    // Teste para o cenário 3: Troco
    @Test
    @DisplayName("CENARIO 3 - Troco: Pagamento.valorPago é usado, preservando troco corretamente")
    void cenario3_trocoPreservado() {
        BigDecimal faturamentoComTroco = new BigDecimal("35.00");
        List<MeioPagamentoItemDTO> pagamentosComTroco = List.of(
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("35.00"))
        );

        List<Pedido> pedidosComTroco = List.of(
                pedido(new BigDecimal("35.00"), StatusPedido.FINALIZADO, TipoPedido.MESA, FormaPagamento.DINHEIRO)
        );

        mockarDashboardBasico(pedidosComTroco, faturamentoComTroco, BigDecimal.ZERO, pagamentosComTroco, List.of(), Collections.<ProdutoRankingDTO>emptyList(), inicio, fim);

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin");

        assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoComTroco);
        assertThat(resultado.getMeiosPagamento()).hasSize(1);
        assertThat(resultado.getMeiosPagamento().get(0).getFormaPagamento()).isEqualTo("DINHEIRO");
        assertThat(resultado.getMeiosPagamento().get(0).getTotalFaturado()).isEqualByComparingTo("35.00");
    }

    // Teste para o cenário 4: Pedido sem Pagamento
    @Test
    @DisplayName("CENARIO 4 - Pedido sem Pagamento: Não deve entrar no faturamento financeiro bruto")
    void cenario4_pedidoSemPagamentoNaoEntraNoFaturamento() {
        List<Pedido> pedidosSemPagamento = List.of(
                pedido(new BigDecimal("60.00"), StatusPedido.FINALIZADO, TipoPedido.DELIVERY, FormaPagamento.PIX)
        );

        mockarDashboardBasico(pedidosSemPagamento, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), Collections.<ProdutoRankingDTO>emptyList(), inicio, fim);

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin");

        assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resultado.getKpis().getTotalPedidos()).isEqualTo(1);
        assertThat(resultado.getKpis().getTicketMedio()).isEqualByComparingTo("60.00");
    }

    // Teste para o cenário 5: Estorno (agora líquido)
    @Test
    @DisplayName("TESTE DE RELATÓRIO 1: Faturamento total desconta estorno")
    void faturamentoTotalDescontaEstorno() {
        BigDecimal pagamentoTotal = new BigDecimal("100.00");
        BigDecimal estornoTotal = new BigDecimal("30.00");
        BigDecimal faturamentoLiquidoEsperado = pagamentoTotal.subtract(estornoTotal);

        mockarDashboardBasico(
                List.of(),
                pagamentoTotal,
                estornoTotal,
                List.of(new MeioPagamentoItemDTO(FormaPagamento.PIX, pagamentoTotal)),
                List.of(new MeioPagamentoItemDTO(FormaPagamento.PIX, estornoTotal)),
                Collections.<ProdutoRankingDTO>emptyList()
        );

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin");
        assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoLiquidoEsperado);
    }

    @Test
    @DisplayName("TESTE DE RELATÓRIO 2, 3, 4, 5: Faturamento por forma de pagamento desconta estorno")
    void faturamentoPorFormaDescontaEstorno() {
        // Pagamentos brutos
        List<MeioPagamentoItemDTO> pagamentosBrutos = List.of(
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("100.00")),
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("200.00")),
                new MeioPagamentoItemDTO(FormaPagamento.CREDITO, new BigDecimal("50.00")),
                new MeioPagamentoItemDTO(FormaPagamento.DEBITO, new BigDecimal("70.00"))
        );

        // Estornos
        List<MeioPagamentoItemDTO> estornos = List.of(
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("30.00")),
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("50.00")),
                new MeioPagamentoItemDTO(FormaPagamento.CREDITO, new BigDecimal("10.00"))
        );

        BigDecimal totalPagamentos = new BigDecimal("420.00"); // 100+200+50+70
        BigDecimal totalEstornos = new BigDecimal("90.00"); // 30+50+10
        BigDecimal faturamentoLiquidoEsperado = totalPagamentos.subtract(totalEstornos);

        mockarDashboardBasico(
                List.of(),
                totalPagamentos,
                totalEstornos,
                pagamentosBrutos,
                estornos,
                Collections.<ProdutoRankingDTO>emptyList()
        );

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin");

        assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoLiquidoEsperado);

        // Verificar por forma de pagamento
        assertThat(resultado.getMeiosPagamento()).containsExactlyInAnyOrder(
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("70.00")), // 100 - 30
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("150.00")),     // 200 - 50
                new MeioPagamentoItemDTO(FormaPagamento.CREDITO, new BigDecimal("40.00")),   // 50 - 10
                new MeioPagamentoItemDTO(FormaPagamento.DEBITO, new BigDecimal("70.00"))     // 70 - 0
        );
    }

    @Test
    @DisplayName("TESTE DE RELATÓRIO 6: Múltiplos pagamentos e múltiplos estornos")
    void multiplosPagamentosMultiplosEstornos() {
        List<MeioPagamentoItemDTO> pagamentosBrutos = List.of(
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("100.00")),
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("50.00")),
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("200.00"))
        );

        List<MeioPagamentoItemDTO> estornos = List.of(
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("20.00")),
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("30.00")),
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("10.00"))
        );

        BigDecimal totalPagamentos = new BigDecimal("350.00"); // 100+50+200
        BigDecimal totalEstornos = new BigDecimal("60.00"); // 20+30+10
        BigDecimal faturamentoLiquidoEsperado = totalPagamentos.subtract(totalEstornos);

        mockarDashboardBasico(
                List.of(),
                totalPagamentos,
                totalEstornos,
                pagamentosBrutos,
                estornos,
                Collections.<ProdutoRankingDTO>emptyList()
        );

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin");

        assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoLiquidoEsperado);
        assertThat(resultado.getMeiosPagamento()).containsExactlyInAnyOrder(
                new MeioPagamentoItemDTO(FormaPagamento.PIX, new BigDecimal("100.00")), // (100+50) - (20+30) = 150 - 50 = 100
                new MeioPagamentoItemDTO(FormaPagamento.DINHEIRO, new BigDecimal("190.00")) // 200 - 10
        );
    }

    @Test
    @DisplayName("TESTE DE RELATÓRIO 7: Pagamento sem estorno permanece integral")
    void pagamentoSemEstornoPermaneceIntegral() {
        BigDecimal pagamentoTotal = new BigDecimal("100.00");
        BigDecimal estornoTotal = BigDecimal.ZERO;
        BigDecimal faturamentoLiquidoEsperado = pagamentoTotal;

        mockarDashboardBasico(
                List.of(),
                pagamentoTotal,
                estornoTotal,
                List.of(new MeioPagamentoItemDTO(FormaPagamento.PIX, pagamentoTotal)),
                List.of(),
                Collections.<ProdutoRankingDTO>emptyList()
        );

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin");
        assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoLiquidoEsperado);
        assertThat(resultado.getMeiosPagamento()).containsExactlyInAnyOrder(
                new MeioPagamentoItemDTO(FormaPagamento.PIX, pagamentoTotal)
        );
    }

    @Test
    @DisplayName("TESTE DE RELATÓRIO 8: Estorno parcial reduz apenas o valor correspondente")
    void estornoParcialReduzValorCorrespondente() {
        BigDecimal pagamentoTotal = new BigDecimal("100.00");
        BigDecimal estornoParcial = new BigDecimal("40.00");
        BigDecimal faturamentoLiquidoEsperado = pagamentoTotal.subtract(estornoParcial);

        mockarDashboardBasico(
                List.of(),
                pagamentoTotal,
                estornoParcial,
                List.of(new MeioPagamentoItemDTO(FormaPagamento.CREDITO, pagamentoTotal)),
                List.of(new MeioPagamentoItemDTO(FormaPagamento.CREDITO, estornoParcial)),
                Collections.<ProdutoRankingDTO>emptyList()
        );

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin");
        assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoLiquidoEsperado);
        assertThat(resultado.getMeiosPagamento()).containsExactlyInAnyOrder(
                new MeioPagamentoItemDTO(FormaPagamento.CREDITO, faturamentoLiquidoEsperado)
        );
    }

    @Test
    @DisplayName("TESTE DE RELATÓRIO 9: Estorno total zera a contribuição financeira")
    void estornoTotalZeraContribuicaoFinanceira() {
        BigDecimal pagamentoTotal = new BigDecimal("100.00");
        BigDecimal estornoTotal = new BigDecimal("100.00");
        BigDecimal faturamentoLiquidoEsperado = BigDecimal.ZERO;

        mockarDashboardBasico(
                List.of(),
                pagamentoTotal,
                estornoTotal,
                List.of(new MeioPagamentoItemDTO(FormaPagamento.DEBITO, pagamentoTotal)),
                List.of(new MeioPagamentoItemDTO(FormaPagamento.DEBITO, estornoTotal)),
                Collections.<ProdutoRankingDTO>emptyList()
        );

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, "admin");
        assertThat(resultado.getKpis().getFaturamentoTotal()).isEqualByComparingTo(faturamentoLiquidoEsperado);
        assertThat(resultado.getMeiosPagamento()).hasSize(1);
        assertThat(resultado.getMeiosPagamento().get(0).getFormaPagamento()).isEqualTo("DEBITO");
        assertThat(resultado.getMeiosPagamento().get(0).getTotalFaturado()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("TESTE DE RELATÓRIO 10: Período financeiro segue a semântica temporal definida (estorno em período diferente)")
    void periodoFinanceiroEstornoEmPeriodoDiferente() {
        LocalDateTime dataPagamento = inicio.plusDays(5);
        LocalDateTime dataEstorno = fim.plusDays(5); // Estorno fora do período do relatório

        BigDecimal pagamentoValor = new BigDecimal("100.00");
        BigDecimal estornoValor = new BigDecimal("50.00");

        // Cenário 1: Relatório para o período do pagamento (sem o estorno)
        mockarDashboardBasico(
                List.of(),
                pagamentoValor, // Pagamento dentro do período
                BigDecimal.ZERO, // Nenhum estorno dentro do período
                List.of(new MeioPagamentoItemDTO(FormaPagamento.PIX, pagamentoValor)),
                List.of(),
                Collections.<ProdutoRankingDTO>emptyList(),
                inicio, fim
        );
        DashboardDataDTO resultadoPagamentoPeriodo = relatorioService.gerarDashboard(inicio, fim, "admin");
        assertThat(resultadoPagamentoPeriodo.getKpis().getFaturamentoTotal()).isEqualByComparingTo(pagamentoValor);

        // Cenário 2: Relatório para o período do estorno (sem o pagamento)
        mockarDashboardBasico(
                List.of(),
                BigDecimal.ZERO, // Nenhum pagamento dentro do período
                estornoValor, // Estorno dentro do período
                List.of(),
                List.of(new MeioPagamentoItemDTO(FormaPagamento.PIX, estornoValor)),
                Collections.<ProdutoRankingDTO>emptyList(),
                fim.plusDays(1), fim.plusDays(10) // Período que inclui o estorno
        );
        DashboardDataDTO resultadoEstornoPeriodo = relatorioService.gerarDashboard(fim.plusDays(1), fim.plusDays(10), "admin");
        assertThat(resultadoEstornoPeriodo.getKpis().getFaturamentoTotal()).isEqualByComparingTo(estornoValor.negate()); // Estorno é uma saída
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

    private Pedido pedido(BigDecimal total, StatusPedido status, TipoPedido tipo, FormaPagamento formaPagamento, LocalDateTime dataHora) {
        Pedido pedido = new Pedido();
        pedido.setTotal(total);
        pedido.setStatus(status);
        pedido.setTipo(tipo);
        pedido.setFormaPagamento(formaPagamento);
        pedido.setDataHora(dataHora);
        return pedido;
    }

    private void mockarDashboardBasico(
            List<Pedido> pedidos,
            BigDecimal faturamentoBruto,
            BigDecimal totalEstornos,
            List<MeioPagamentoItemDTO> pagamentosBrutos,
            List<MeioPagamentoItemDTO> estornosPorForma,
            List<ProdutoRankingDTO> produtos
    ) {
        mockarDashboardBasico(pedidos, faturamentoBruto, totalEstornos, pagamentosBrutos, estornosPorForma, produtos, inicio, fim);
    }

    private void mockarDashboardBasico(
            List<Pedido> pedidos,
            BigDecimal faturamentoBruto,
            BigDecimal totalEstornos,
            List<MeioPagamentoItemDTO> pagamentosBrutos,
            List<MeioPagamentoItemDTO> estornosPorForma,
            List<ProdutoRankingDTO> produtos,
            LocalDateTime mockInicio,
            LocalDateTime mockFim
    ) {
        when(pedidoRepository.buscarPedidosParaRelatorio(mockInicio, mockFim)).thenReturn(pedidos);
        when(pagamentoRepository.somarFaturamentoBrutoPorPeriodo(mockInicio, mockFim)).thenReturn(faturamentoBruto);
        when(pagamentoRepository.somarFaturamentoPorMeioPagamentoPorPeriodo(mockInicio, mockFim)).thenReturn(pagamentosBrutos);
        when(estornoPagamentoRepository.somarTotalEstornosPorPeriodo(mockInicio, mockFim)).thenReturn(totalEstornos);
        when(estornoPagamentoRepository.somarEstornosPorMeioPagamentoPorPeriodo(mockInicio, mockFim)).thenReturn(estornosPorForma);
        when(pedidoRepository.buscarTopProdutosJPQL(eq(mockInicio), eq(mockFim), eq(StatusPedido.FINALIZADO), any(Pageable.class))).thenReturn(produtos);
    }

    private void verifyNoMoreInteractionsAposAgregacoes() {
        verify(pagamentoRepository).somarFaturamentoBrutoPorPeriodo(inicio, fim);
        verify(pagamentoRepository).somarFaturamentoPorMeioPagamentoPorPeriodo(inicio, fim);
        verify(estornoPagamentoRepository).somarTotalEstornosPorPeriodo(inicio, fim);
        verify(estornoPagamentoRepository).somarEstornosPorMeioPagamentoPorPeriodo(inicio, fim);
        verify(pedidoRepository).buscarTopProdutosJPQL(eq(inicio), eq(fim), eq(StatusPedido.FINALIZADO), any(Pageable.class));
        verifyNoMoreInteractions(pedidoRepository, pagamentoRepository, estornoPagamentoRepository);
    }
}