package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class MeioPagamentoItemDTO {
    private String formaPagamento;
    private BigDecimal totalFaturado;

    // Construtor inteligente: Recebe o Enum do banco e já converte pra String
    public MeioPagamentoItemDTO(FormaPagamento formaPagamento, BigDecimal totalFaturado) {
        this.formaPagamento = formaPagamento != null ? formaPagamento.name() : "N/A";
        this.totalFaturado = totalFaturado;
    }
}