package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Entidade que gerencia os lotes de lanches.
 * Em vendas de salão, pertence estritamente a uma Conta (que por sua vez aponta para a Comanda).
 * Em vendas de Balcão/Delivery, a Conta fica nula e usa-se o Cliente diretamente.
 */
@Entity
@Table(name = "pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, unique = true, length = 10)
    private String numeroPedido;

    /**
     * 🎯 ARQUITETURA PURISTA (O SEGREDO DA NAVEGAÇÃO):
     * A antiga FK "comanda_id" foi removida. O Pedido de salão ancora-se na Conta.
     * Permitimos nullable = true para suportar Pedidos de Delivery ou Balcão que não usam Mesa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id", nullable = true)
    @JsonIgnoreProperties({"pedidos", "cliente", "pagamentos", "comanda"})
    private Conta conta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "conta"})
    private Cliente cliente;

    @Column(length = 100)
    private String nomeClienteBalcao;

    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusFinanceiro statusFinanceiro = StatusFinanceiro.AGUARDANDO_PAGAMENTO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPedido tipo;

    private Integer numeroMesa;

    private String enderecoEntrega;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 255)
    private String observacaoGeral;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FormaPagamento formaPagamento;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorRecebido;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("pedido")
    private List<ItemPedido> itens = new ArrayList<>();

    @PrePersist
    public void gerarNumeroPedido() {
        if (this.numeroPedido == null) {
            String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder codigo = new StringBuilder();
            Random rnd = new Random();
            while (codigo.length() < 5) {
                int index = (int) (rnd.nextFloat() * caracteres.length());
                codigo.append(caracteres.charAt(index));
            }
            this.numeroPedido = codigo.toString();
        }
    }
}