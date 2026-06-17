package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnore; // 🚨 Importação necessária
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 🚨 Importação necessária
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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

    @NotBlank(message = "O nome não pode ser nulo ou em branco.")
    @Column(nullable = false, length = 150)
    private String nome;

    @NotBlank(message = "A descrição não pode ser nula ou em branco.")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @NotNull(message = "O preço é obrigatório.")
    @Positive(message = "O preço deve ser um valor maior que zero.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "url_imagem")
    private String urlImagem;

    @NotNull(message = "O status do produto é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusProduto status;

    @NotNull
    @Column(nullable = false)
    private Boolean isCombo = false;

    // 🚀 NOVO CAMPO: Define se o produto exige fabricação ou se é entregue direto no balcão
    @NotNull
    @Column(name = "precisa_preparo", nullable = false)
    private Boolean precisaPreparo = true;

    @NotNull(message = "A categoria é obrigatória.")
    // 🚨 A MÁGICA 1: Diz pro Jackson não surtar com o Proxy Lazy do Hibernate
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    // 🚨 A MÁGICA 2: Ignora essas listas na hora de gerar o JSON para não dar erro de LazyLoading
    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "produto_adicional",
            joinColumns = @JoinColumn(name = "produto_id"),
            inverseJoinColumns = @JoinColumn(name = "adicional_id")
    )
    private List<Adicional> adicionais = new ArrayList<>();

    // 🚨 A MÁGICA 3: Também ignora os combos para blindar o JSON
    @JsonIgnore
    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComboProduto> itensDoCombo = new ArrayList<>();
}