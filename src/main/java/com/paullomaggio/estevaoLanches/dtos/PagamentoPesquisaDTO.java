package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Pagamento;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "DTO de resposta para pesquisa de pagamentos na tela de Estornos")
public class PagamentoPesquisaDTO {

    @Schema(description = "ID do pagamento", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID idPagamento;

    @Schema(description = "Nome do cliente", example = "João da Silva")
    private String cliente;

    @Schema(description = "Número da mesa", example = "5")
    private Integer numeroMesa;

    @Schema(description = "ID do pedido", example = "550e8400-e29b-41d4-a716-446655440001")
    private UUID pedidoId;

    @Schema(description = "Número do pedido", example = "ABC12")
    private String numeroPedido;

    @Schema(description = "Forma de pagamento", example = "PIX")
    private FormaPagamento formaPagamento;

    @Schema(description = "Valor pago", example = "25.50")
    private BigDecimal valorPago;

    @Schema(description = "Saldo estornável", example = "25.50")
    private BigDecimal saldoEstornavel;

    @Schema(description = "Status do pagamento", example = "PAGO")
    private StatusPagamento statusPagamento;

    @Schema(description = "Data e hora do pagamento", example = "2026-04-08T14:30:00")
    private LocalDateTime dataPagamento;

    @Schema(description = "Usuário responsável pelo pagamento", example = "garcom@estevao.com.br")
    private String usuarioResponsavel;

    @Schema(description = "ID do caixa associado", example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID caixaId;

    public PagamentoPesquisaDTO(Pagamento pagamento, BigDecimal saldoEstornavel) {
        this.idPagamento = pagamento.getId();
        this.cliente = pagamento.getConta() != null
                && pagamento.getConta().getCliente() != null
                ? pagamento.getConta().getCliente().getNome()
                : (pagamento.getConta() != null
                    ? pagamento.getConta().getNomeResponsavel()
                    : null);
        this.numeroMesa = pagamento.getPedido() != null ? pagamento.getPedido().getNumeroMesa() : null;
        this.pedidoId = pagamento.getPedido() != null ? pagamento.getPedido().getId() : null;
        this.numeroPedido = pagamento.getPedido() != null ? pagamento.getPedido().getNumeroPedido() : null;
        this.formaPagamento = pagamento.getFormaPagamento();
        this.valorPago = pagamento.getValorPago();
        this.saldoEstornavel = saldoEstornavel;
        this.statusPagamento = pagamento.getConta() != null
                ? (pagamento.getConta().getPago() ? StatusPagamento.PAGO : StatusPagamento.ABERTO)
                : StatusPagamento.PAGO;
        this.dataPagamento = pagamento.getDataHora();
        this.usuarioResponsavel = pagamento.getUsuarioResponsavel();
        this.caixaId = pagamento.getCaixa() != null ? pagamento.getCaixa().getId() : null;
    }
}
