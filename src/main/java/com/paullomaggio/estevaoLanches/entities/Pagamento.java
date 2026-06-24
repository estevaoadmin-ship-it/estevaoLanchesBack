package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade responsável por registrar as transações financeiras da lanchonete.
 * Aponta fisicamente para a Conta correspondente, suportando amortizações parciais.
 */
@Entity
@Table(name = "pagamento")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 🎯 RELACIONAMENTO FÍSICO AJUSTADO: O pagamento agora pertence explicitamente
     * a uma única Conta mestre através da FK conta_id.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id", nullable = false)
    @JsonIgnoreProperties({"pagamentos", "pedidos", "cliente"})
    private Conta conta;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FormaPagamento formaPagamento;

    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    private String usuarioResponsavel;

    // 🛡️ Getters e Setters manuais à prova de falhas do compilador
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }

    public Conta getConta() {
        return conta;
    }
    public void setConta(Conta conta) {
        this.conta = conta;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }
    public void setValorPago(BigDecimal valorPago) {
        this.valorPago = valorPago;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }
    public void setFormaPagamento(FormaPagamento formaPagamento) {
        this.formaPagamento = formaPagamento;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }
    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getUsuarioResponsavel() {
        return usuarioResponsavel;
    }
    public void setUsuarioResponsavel(String usuarioResponsavel) {
        this.usuarioResponsavel = usuarioResponsavel;
    }
}