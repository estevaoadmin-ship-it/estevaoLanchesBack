package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Entidade que define a estrutura de um Combo no Cardápio (Master Data).
 * Liga um Produto Pai (tipo COMBO) aos seus Produtos Filhos (lanches, bebidas).
 */
@Entity
@Table(name = "combo_produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ComboProduto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    // Este é o Produto "Pai" (O Combo Estevão)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Produto combo;

    // Este é o Produto "Filho" (O X-Burguer que vai dentro do combo)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidade;
}