package com.paullomaggio.estevaoLanches.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "item_carrinho")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ItemCarrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    // A qual carrinho este item pertence?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrinho_id", nullable = false)
    private Carrinho carrinho;

    // Qual é o produto escolhido?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidade;

    // Ex: "Tirar a maionese", "Hambúrguer bem passado"
    @Column(length = 255)
    private String observacao;

    // Adicionais selecionados especificamente para este item do carrinho
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "item_carrinho_adicional",
            joinColumns = @JoinColumn(name = "item_carrinho_id"),
            inverseJoinColumns = @JoinColumn(name = "adicional_id")
    )
    @ToString.Exclude // Evita LazyInitializationException e recursão em toString
    private Set<Adicional> adicionais = new HashSet<>();
}