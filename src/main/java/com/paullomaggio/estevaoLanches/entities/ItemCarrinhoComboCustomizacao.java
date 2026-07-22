package com.paullomaggio.estevaoLanches.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "item_carrinho_combo_customizacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ItemCarrinhoComboCustomizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_carrinho_id", nullable = false)
    private ItemCarrinho itemCarrinho;

    @Column(name = "combo_produto_id", nullable = false)
    private UUID comboProdutoId; // ID do ComboProduto que está sendo customizado

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "item_carrinho_combo_customizacao_adicional",
            joinColumns = @JoinColumn(name = "item_carrinho_combo_customizacao_id"),
            inverseJoinColumns = @JoinColumn(name = "adicional_id")
    )
    @ToString.Exclude
    private Set<Adicional> adicionais = new HashSet<>();

    @Column(name = "observacao", length = 255)
    private String observacao;
}