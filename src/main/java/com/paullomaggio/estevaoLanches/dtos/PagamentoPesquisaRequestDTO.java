package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "DTO de requisição para pesquisa de pagamentos na tela de Estornos")
public class PagamentoPesquisaRequestDTO {

    @Schema(description = "ID do cliente para filtro", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID clienteId;

    @Schema(description = "ID da mesa para filtro", example = "550e8400-e29b-41d4-a716-446655440001")
    private UUID mesaId;

    @Schema(description = "ID do pedido para filtro", example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID pedidoId;

    @Schema(description = "Forma de pagamento para filtro", example = "PIX")
    private FormaPagamento formaPagamento;

    @Schema(description = "Status do pagamento para filtro", example = "PAGO")
    private StatusPagamento statusPagamento;

    @Schema(description = "Data/hora inicial para filtro de período", example = "2026-01-01T00:00:00")
    private LocalDateTime dataInicial;

    @Schema(description = "Data/hora final para filtro de período", example = "2026-04-08T23:59:59")
    private LocalDateTime dataFinal;

    @Schema(description = "ID do caixa para filtro", example = "550e8400-e29b-41d4-a716-446655440003")
    private UUID caixaId;

    @Schema(description = "ID da conta para filtro", example = "550e8400-e29b-41d4-a716-446655440004")
    private UUID contaId;

    @Schema(description = "Número da mesa para filtro (busca direta por número)", example = "5")
    private Integer numeroMesa;

    @Schema(description = "Termo de busca no nome do cliente", example = "Silva")
    private String nomeCliente;

    @Schema(description = "Número do pedido para filtro", example = "ABC12")
    private String numeroPedido;

    @Schema(description = "Valor mínimo para filtro", example = "10.00")
    private BigDecimal valorMinimo;

    @Schema(description = "Valor máximo para filtro", example = "100.00")
    private BigDecimal valorMaximo;
}
