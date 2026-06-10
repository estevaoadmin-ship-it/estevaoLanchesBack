package com.paullomaggio.estevaoLanches.entities;

import jakarta.persistence.*;
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

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(length = 255)
    private String descricao;

    // Ajuda a ordenar o cardápio no app do cliente (Lanches 1º, Bebidas 2º)
    @Column(nullable = false)
    private Integer ordemExibicao = 0;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Ícone ou foto da categoria para o App
    private String urlImagem;
}
