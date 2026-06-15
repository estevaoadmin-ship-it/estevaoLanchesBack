package com.paullomaggio.estevaoLanches.services;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
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
import java.util.List;

@Service
public class RelatorioService {

    @Autowired
    private PedidoRepository pedidoRepository;

    public DashboardDataDTO gerarDashboard(LocalDateTime inicio, LocalDateTime fim, String usuarioId) {
        // Nota: O parâmetro usuarioId permanece na assinatura para compatibilidade com a API,
        // mas não é enviado ao repositório porque Pedido não possui essa coluna no banco.
        List<Pedido> pedidos = pedidoRepository.buscarPedidosParaRelatorio(inicio, fim);
        DashboardStatsDTO kpis = calcularKPIs(pedidos);

        List<MeioPagamentoItemDTO> pagamentos = pedidoRepository.somarFaturamentoPorMeioPagamento(inicio, fim, StatusPedido.FINALIZADO);

        Pageable limit5 = PageRequest.of(0, 5);
        List<ProdutoRankingDTO> topProdutos = pedidoRepository.buscarTopProdutosJPQL(inicio, fim, StatusPedido.FINALIZADO, limit5);

        return new DashboardDataDTO(kpis, pagamentos, topProdutos);
    }

    private DashboardStatsDTO calcularKPIs(List<Pedido> pedidos) {
        BigDecimal faturamentoTotal = BigDecimal.ZERO;
        long totalPedidos = 0;
        long totalCancelamentos = 0;
        BigDecimal perdaCancelamentos = BigDecimal.ZERO;

        for (Pedido p : pedidos) {
            if (p.getStatus() == StatusPedido.FINALIZADO) {
                faturamentoTotal = faturamentoTotal.add(p.getTotal());
                totalPedidos++;
            } else if (p.getStatus() == StatusPedido.CANCELADO) {
                perdaCancelamentos = perdaCancelamentos.add(p.getTotal());
                totalCancelamentos++;
            }
        }

        BigDecimal ticketMedio = BigDecimal.ZERO;
        if (totalPedidos > 0) {
            ticketMedio = faturamentoTotal.divide(BigDecimal.valueOf(totalPedidos), 2, RoundingMode.HALF_UP);
        }

        return new DashboardStatsDTO(faturamentoTotal, totalPedidos, ticketMedio, totalCancelamentos, perdaCancelamentos);
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
            kpiTable.addCell(createCell("Faturamento Bruto", true));
            kpiTable.addCell(createCell("Total Pedidos", true));
            kpiTable.addCell(createCell("Ticket Médio", true));
            kpiTable.addCell(createCell("Perdas (Cancelados)", true));

            kpiTable.addCell(createCell("R$ " + dados.getKpis().getFaturamentoTotal(), false));
            kpiTable.addCell(createCell(String.valueOf(dados.getKpis().getTotalPedidos()), false));
            kpiTable.addCell(createCell("R$ " + dados.getKpis().getTicketMedio(), false));
            kpiTable.addCell(createCell("R$ " + dados.getKpis().getPerdaCancelamentos(), false));

            document.add(kpiTable);
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