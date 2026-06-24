package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoriaRequestDTO(
        @NotBlank(message = "O nome da categoria é obrigatório e não pode conter apenas espaços.")
        @Size(max = 100, message = "O nome da categoria não pode ultrapassar 100 caracteres.")
        String nome,

        @Size(max = 255, message = "A descrição não pode ultrapassar 255 caracteres.")
        String descricao,

        @NotNull(message = "A ordem de exibição é obrigatória.")
        @Min(value = 0, message = "A ordem de exibição deve ser um valor maior ou igual a 0.")
        Integer ordemExibicao,

        @NotNull(message = "O status ativo é obrigatório.")
        Boolean ativo,

        @Size(max = 255, message = "O link da imagem não pode ultrapassar 255 caracteres.")
        String urlImagem
) {
    public CategoriaRequestDTO {
        if (nome != null) nome = nome.trim().toUpperCase();
        if (ordemExibicao == null) ordemExibicao = 0;
        if (ativo == null) ativo = true;
    }
}