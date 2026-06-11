package com.paullomaggio.estevaoLanches.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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
}