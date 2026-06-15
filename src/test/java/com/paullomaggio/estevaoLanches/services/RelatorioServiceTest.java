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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private RelatorioService relatorioService;

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

    @Test
    @DisplayName("CT-REL-001: Deve calcular KPIs corretamente")
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
    @DisplayName("CT-REL-002: Deve evitar exceção de divisão por zero")
    void deveLidarComPeriodoSemVendas() {
        when(pedidoRepository.buscarPedidosParaRelatorio(any(), any())).thenReturn(Collections.emptyList());
        when(pedidoRepository.somarFaturamentoPorMeioPagamento(any(), any(), any())).thenReturn(Collections.emptyList());
        when(pedidoRepository.buscarTopProdutosJPQL(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        DashboardDataDTO resultado = relatorioService.gerarDashboard(inicio, fim, null);

        assertThat(resultado.getKpis().getTicketMedio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("CT-REL-003: Deve tratar as Strings vazias")
    void deveSanitizarIdDoOperadorParaQuery() {
        when(pedidoRepository.buscarPedidosParaRelatorio(eq(inicio), eq(fim))).thenReturn(pedidosMock);
        relatorioService.gerarDashboard(inicio, fim, "");
    }

    @Test
    @DisplayName("CT-REL-004: Deve gerar bytes do relatório em PDF validos")
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
    @DisplayName("CT-REL-005: Deve empacotar falhas de PDF como RuntimeException")
    void deveLancarExcecaoAoFalharGeracaoPdf() {
        when(pedidoRepository.buscarPedidosParaRelatorio(any(), any())).thenThrow(new RuntimeException("Banco falhou"));

        assertThrows(RuntimeException.class, () -> relatorioService.exportarRelatorioPdf(inicio, fim, null));
    }
}