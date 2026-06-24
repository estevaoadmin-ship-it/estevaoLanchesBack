package com.paullomaggio.estevaoLanches.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
// 🛡️ BLINDAGEM MÁGICA: Evita quebras de proxy quando a categoria for carregada de forma Lazy por um Produto
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @NotBlank(message = "O nome da categoria não pode ser nulo ou em branco.")
    @Column(nullable = false, length = 100)
    private String nome;

    @Size(max = 255)
    @Column(length = 255)
    private String descricao;

    @NotNull(message = "A ordem de exibição é obrigatória.")
    @Min(0)
    @Column(nullable = false)
    private Integer ordemExibicao = 0;

    @NotNull(message = "O status ativo é obrigatório.")
    @Column(nullable = false)
    private Boolean ativo = true;

    @Size(max = 255)
    @Column(name = "url_imagem")
    private String urlImagem;

    @JsonIgnore
    @OneToMany(mappedBy = "categoria", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Produto> produtos = new ArrayList<>();
}