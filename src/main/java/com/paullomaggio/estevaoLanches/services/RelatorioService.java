package com.paullomaggio.estevaoLanches.services;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.repositories.EstornoPagamentoRepository;
import com.paullomaggio.estevaoLanches.repositories.PagamentoRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap; // Import LinkedHashMap
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RelatorioService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private EstornoPagamentoRepository estornoPagamentoRepository;

    public DashboardDataDTO gerarDashboard(LocalDateTime inicio, LocalDateTime fim, String usuarioId) {
        List<Pedido> pedidos = pedidoRepository.buscarPedidosParaRelatorio(inicio, fim);
        DashboardStatsDTO kpisComerciais = calcularKPIsComerciais(pedidos);

        BigDecimal faturamentoBrutoRecebido = pagamentoRepository.somarFaturamentoBrutoPorPeriodo(inicio, fim);
        BigDecimal totalEstornosNoPeriodo = estornoPagamentoRepository.somarTotalEstornosPorPeriodo(inicio, fim);
        BigDecimal faturamentoLiquido = faturamentoBrutoRecebido.subtract(totalEstornosNoPeriodo);

        List<MeioPagamentoItemDTO> pagamentosPorFormaBruto = pagamentoRepository.somarFaturamentoPorMeioPagamentoPorPeriodo(inicio, fim);
        List<MeioPagamentoItemDTO> estornosPorForma = estornoPagamentoRepository.somarEstornosPorMeioPagamentoPorPeriodo(inicio, fim);

        // Consolidar pagamentos brutos por forma, preservando a ordem da primeira ocorrência
        Map<String, BigDecimal> pagamentosConsolidados =
                pagamentosPorFormaBruto.stream()
                        .collect(Collectors.toMap(
                                MeioPagamentoItemDTO::getFormaPagamento,
                                MeioPagamentoItemDTO::getTotalFaturado,
                                BigDecimal::add,
                                LinkedHashMap::new
                        ));

        // Consolidar estornos por forma, preservando a ordem da primeira ocorrência
        Map<String, BigDecimal> estornosConsolidados =
                estornosPorForma.stream()
                        .collect(Collectors.toMap(
                                MeioPagamentoItemDTO::getFormaPagamento,
                                MeioPagamentoItemDTO::getTotalFaturado,
                                BigDecimal::add,
                                LinkedHashMap::new
                        ));

        // Calcular o faturamento líquido por forma de pagamento
        List<MeioPagamentoItemDTO> pagamentosPorFormaLiquido =
                pagamentosConsolidados.entrySet()
                        .stream()
                        .map(entry -> {
                            BigDecimal totalEstornado =
                                    estornosConsolidados.getOrDefault(
                                            entry.getKey(),
                                            BigDecimal.ZERO
                                    );

                            BigDecimal valorLiquido =
                                    entry.getValue().subtract(totalEstornado);

                            return new MeioPagamentoItemDTO(
                                    FormaPagamento.valueOf(entry.getKey()),
                                    valorLiquido
                            );
                        })
                        .collect(Collectors.toList());

        Pageable limit5 = PageRequest.of(0, 5);
        List<ProdutoRankingDTO> topProdutos = pedidoRepository.buscarTopProdutosJPQL(inicio, fim, StatusPedido.FINALIZADO, limit5);

        DashboardStatsDTO kpisFinais = new DashboardStatsDTO(
                faturamentoLiquido,
                kpisComerciais.getTotalPedidos(),
                kpisComerciais.getTicketMedio(),
                kpisComerciais.getTotalCancelamentos(),
                kpisComerciais.getPerdaCancelamentos()
        );

        return new DashboardDataDTO(kpisFinais, pagamentosPorFormaLiquido, topProdutos);
    }

    private DashboardStatsDTO calcularKPIsComerciais(List<Pedido> pedidos) {
        BigDecimal faturamentoComercialPedidosFinalizados = BigDecimal.ZERO;
        long totalPedidos = 0;
        long totalCancelamentos = 0;
        BigDecimal perdaCancelamentos = BigDecimal.ZERO;

        for (Pedido p : pedidos) {
            if (p.getStatus() == StatusPedido.FINALIZADO) {
                faturamentoComercialPedidosFinalizados = faturamentoComercialPedidosFinalizados.add(p.getTotal());
                totalPedidos++;
            } else if (p.getStatus() == StatusPedido.CANCELADO) {
                perdaCancelamentos = perdaCancelamentos.add(p.getTotal());
                totalCancelamentos++;
            }
        }

        BigDecimal ticketMedio = BigDecimal.ZERO;
        if (totalPedidos > 0) {
            ticketMedio = faturamentoComercialPedidosFinalizados.divide(BigDecimal.valueOf(totalPedidos), 2, RoundingMode.HALF_UP);
        }

        return new DashboardStatsDTO(faturamentoComercialPedidosFinalizados, totalPedidos, ticketMedio, totalCancelamentos, perdaCancelamentos);
    }

    public byte[] exportarRelatorioPdf(LocalDateTime inicio, LocalDateTime fim, String usuarioId) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            DashboardDataDTO dados = gerarDashboard(inicio, fim, usuarioId);

            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Relatório Gerencial - Tevão Lanches", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            Paragraph subtitulo = new Paragraph("Período analisado: " + inicio.format(dtf) + " até " + fim.format(dtf));
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            subtitulo.setSpacingAfter(20);
            document.add(subtitulo);

            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.addCell(createCell("Faturamento Líquido", true));
            kpiTable.addCell(createCell("Total Pedidos", true));
            kpiTable.addCell(createCell("Ticket Médio", true));
            kpiTable.addCell(createCell("Perdas (Cancelados)", true));

            kpiTable.addCell(createCell("R$ " + dados.getKpis().getFaturamentoTotal(), false));
            kpiTable.addCell(createCell(String.valueOf(dados.getKpis().getTotalPedidos()), false));
            kpiTable.addCell(createCell("R$ " + dados.getKpis().getTicketMedio(), false));
            kpiTable.addCell(createCell("R$ " + dados.getKpis().getPerdaCancelamentos(), false));

            document.add(kpiTable);
            document.add(new Paragraph("\n"));

            Paragraph subtituloPagamentosPorForma = new Paragraph("Faturamento Líquido por Forma de Pagamento", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
            subtituloPagamentosPorForma.setSpacingAfter(10);
            document.add(subtituloPagamentosPorForma);

            PdfPTable pagamentosPorFormaTable = new PdfPTable(2);
            pagamentosPorFormaTable.setWidthPercentage(100);
            pagamentosPorFormaTable.addCell(createCell("Forma de Pagamento", true));
            pagamentosPorFormaTable.addCell(createCell("Valor Líquido", true));

            for (MeioPagamentoItemDTO item : dados.getMeiosPagamento()) {
                pagamentosPorFormaTable.addCell(createCell(item.getFormaPagamento(), false));
                pagamentosPorFormaTable.addCell(createCell("R$ " + item.getTotalFaturado().setScale(2, RoundingMode.HALF_UP), false));
            }
            document.add(pagamentosPorFormaTable);
            document.add(new Paragraph("\n"));


            Paragraph subtituloLanches = new Paragraph("Top 5 Produtos Mais Vendidos", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
            subtituloLanches.setSpacingAfter(10);
            document.add(subtituloLanches);

            PdfPTable produtosTable = new PdfPTable(2);
            produtosTable.setWidthPercentage(100);
            produtosTable.addCell(createCell("Nome do Produto", true));
            produtosTable.addCell(createCell("Qtd Vendida", true));

            for (ProdutoRankingDTO prod : dados.getTopProdutos()) {
                produtosTable.addCell(createCell(prod.getNomeProduto(), false));
                produtosTable.addCell(createCell(String.valueOf(prod.getQuantidadeVendida()), false));
            }
            document.add(produtosTable);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF", e);
        }
    }

    private PdfPCell createCell(String text, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text));
        cell.setPadding(8);
        if (isHeader) {
            cell.setBackgroundColor(new java.awt.Color(230, 230, 230));
        }
        return cell;
    }
}