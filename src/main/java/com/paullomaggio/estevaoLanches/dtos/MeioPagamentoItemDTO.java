package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Schema(description = "DTO contendo o faturamento total por uma forma de pagamento específica")
public class MeioPagamentoItemDTO {
    @Schema(
        description = "Forma de pagamento",
        allowableValues = {"DINHEIRO", "CARTAO_CREDITO", "CARTAO_DEBITO", "PIX", "N/A"},
        example = "PIX"
    )
    private String formaPagamento;
    @Schema(description = "Valor total faturado por esta forma de pagamento", example = "500.00")
    private BigDecimal totalFaturado;

    // Construtor inteligente: Recebe o Enum do banco e já converte pra String
    public MeioPagamentoItemDTO(FormaPagamento formaPagamento, BigDecimal totalFaturado) {
        this.formaPagamento = formaPagamento != null ? formaPagamento.name() : "N/A";
        this.totalFaturado = totalFaturado;
    }
}