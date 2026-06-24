package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
// 🛡️ BLINDAGEM MÁGICA: Garante estabilidade total nos mapeamentos Lazy de ItemPedido, Carrinho e ComboProduto
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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

    @NotNull
    @Column(name = "precisa_preparo", nullable = false)
    private Boolean precisaPreparo = true;

    @NotNull(message = "A categoria é obrigatória.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    @JsonIgnoreProperties("produtos")
    private Categoria categoria;

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "produto_adicional",
            joinColumns = @JoinColumn(name = "produto_id"),
            inverseJoinColumns = @JoinColumn(name = "adicional_id")
    )
    private List<Adicional> adicionais = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComboProduto> itensDoCombo = new ArrayList<>();
}