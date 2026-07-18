package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check; // Import for @Check annotation
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade responsável por registrar as transações financeiras da lanchonete.
 * Aponta fisicamente para a Conta correspondente, suportando amortizações parciais.
 */
@Entity
@Table(name = "pagamento")
@Check(constraints = """
    (conta_id IS NOT NULL AND pedido_id IS NULL)
    OR
    (conta_id IS NULL AND pedido_id IS NOT NULL)
""")
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
    @JoinColumn(name = "conta_id") // Alterado para nullable = true (padrão)
    @JsonIgnoreProperties({"pagamentos", "pedidos", "cliente"})
    private Conta conta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // Adicionado para evitar recursão
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) // Adicionado para evitar recursão
    private Caixa caixa;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FormaPagamento formaPagamento;

    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    @Column(nullable = false) // Tornando usuarioResponsavel obrigatório
    private String usuarioResponsavel;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {

        boolean possuiConta = conta != null;
        boolean possuiPedido = pedido != null;

        if (possuiConta == possuiPedido) {
            throw new IllegalStateException(
                    "Pagamento deve estar vinculado exclusivamente a uma Conta ou a um Pedido."
            );
        }

        if (caixa == null) {
            throw new IllegalStateException(
                    "Pagamento deve estar vinculado a um Caixa."
            );
        }

        if (valorPago == null || valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Pagamento deve possuir valor maior que zero."
            );
        }

        if (formaPagamento == null) {
            throw new IllegalStateException(
                    "Pagamento deve possuir forma de pagamento."
            );
        }

        if (dataHora == null) {
            throw new IllegalStateException(
                    "Pagamento deve possuir data e hora."
            );
        }

        // Validação para usuarioResponsavel
        if (usuarioResponsavel == null || usuarioResponsavel.isBlank()) {
            throw new IllegalStateException(
                    "Pagamento deve possuir usuário responsável."
            );
        }
    }

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

    public Pedido getPedido() {
        return pedido;
    }
    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public Caixa getCaixa() {
        return caixa;
    }
    public void setCaixa(Caixa caixa) {
        this.caixa = caixa;
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