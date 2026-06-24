package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidade que representa a partição financeira (subconta) de um atendimento.
 * Pertence a uma Comanda e vincula-se de forma exclusiva a um Cliente.
 */
@Entity
@Table(name = "conta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Conta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private Integer numeroConta;

    @Column(nullable = false)
    private Boolean pago = false;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    /**
     * 🎯 NOVA LÓGICA: Vinculo reverso com a Comanda Mestre da mesa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comanda_id", nullable = false)
    @JsonIgnoreProperties("contas")
    private Comanda comanda;

    /**
     * 🎯 NOVA LÓGICA: Uma conta tem exatamente um cliente associado (Relação 1:1).
     */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "cliente_id", nullable = false)
    @JsonIgnoreProperties("conta")
    private Cliente cliente;

    @OneToMany(mappedBy = "conta", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("conta")
    private List<Pedido> pedidos = new ArrayList<>();

    @OneToMany(mappedBy = "conta", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("conta")
    private List<Pagamento> pagamentos = new ArrayList<>();
}