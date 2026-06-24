package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entidade que registra de forma atômica e histórica quais produtos
 * foram processados e faturados dentro do item de um pedido que vendeu um Combo.
 */
@Entity
@Table(name = "item_combo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ItemCombo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_pedido_id", nullable = false)
    @JsonIgnoreProperties("itensCombo")
    private ItemPedido itemPedido;

    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    @Column(name = "nome_produto", nullable = false, length = 150)
    private String nomeProduto;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;
}