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
 * Pertence a uma Comanda e vincula-se de forma opcional a um Cliente (Delivery/Retirada)
 * ou possui identificação direta do responsável (Mesa).
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

    @Column(length = 100)
    private String nomeResponsavel;

    @Column(length = 20)
    private String telefoneResponsavel;

    /**
     * 🎯 NOVA LÓGICA: Vinculo reverso com a Comanda Mestre da mesa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comanda_id", nullable = false)
    @JsonIgnoreProperties("contas")
    private Comanda comanda;

    /**
     * Mantido para compatibilidade com Delivery e Retirada.
     * nullable alterado para true para suportar o fluxo puro de Mesa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = true)
    @JsonIgnoreProperties("contas")
    private Cliente cliente;

    @OneToMany(mappedBy = "conta", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("conta")
    private List<Pedido> pedidos = new ArrayList<>();

    @OneToMany(mappedBy = "conta", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("conta")
    private List<Pagamento> pagamentos = new ArrayList<>();
}