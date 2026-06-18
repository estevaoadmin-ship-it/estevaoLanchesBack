package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class ContaPagamentoRequestDTO {

    @NotNull
    private Integer numeroConta;

    @NotNull
    private BigDecimal valorPago;

    @NotNull
    private String formaPagamento;

    private String usuarioResponsavel;

    // Getters e Setters manuais para evitar falhas do compilador
    public Integer getNumeroConta() { return numeroConta; }
    public void setNumeroConta(Integer numeroConta) { this.numeroConta = numeroConta; }

    public BigDecimal getValorPago() { return valorPago; }
    public void setValorPago(BigDecimal valorPago) { this.valorPago = valorPago; }

    public String getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(String formaPagamento) { this.formaPagamento = formaPagamento; }

    public String getUsuarioResponsavel() { return usuarioResponsavel; }
    public void setUsuarioResponsavel(String usuarioResponsavel) { this.usuarioResponsavel = usuarioResponsavel; }
}