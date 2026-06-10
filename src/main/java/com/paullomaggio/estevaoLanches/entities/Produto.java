package com.paullomaggio.estevaoLanches.entities;

import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    // Salva apenas a URL da imagem armazenada na nuvem
    private String urlImagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusProduto status;

    @Column(nullable = false)
    private Boolean isCombo = false;

    // Relacionamento de Muitos para Muitos com Adicionais
    @ManyToMany
    @JoinTable(
            name = "produto_adicional",
            joinColumns = @JoinColumn(name = "produto_id"),
            inverseJoinColumns = @JoinColumn(name = "adicional_id")
    )
    private List<Adicional> adicionais = new ArrayList<>(); // Inicializado para evitar NullPointerException

    // Relacionamento com os itens do combo (explicado abaixo)
    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComboProduto> itensDoCombo = new ArrayList<>();
}