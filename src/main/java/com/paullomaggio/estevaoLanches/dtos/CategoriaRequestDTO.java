package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "DTO para criação ou atualização de uma categoria de produto")
public record CategoriaRequestDTO(
        @Schema(
            description = "Nome da categoria",
            example = "LANCHES",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "O nome da categoria é obrigatório e não pode conter apenas espaços.")
        @Size(max = 100, message = "O nome da categoria não pode ultrapassar 100 caracteres.")
        String nome,

        @Schema(
            description = "Descrição da categoria",
            example = "Sanduíches e hambúrgueres diversos."
        )
        @Size(max = 255, message = "A descrição não pode ultrapassar 255 caracteres.")
        String descricao,

        @Schema(
            description = "Ordem de exibição da categoria na interface do usuário",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "A ordem de exibição é obrigatória.")
        @Min(value = 0, message = "A ordem de exibição deve ser um valor maior ou igual a 0.")
        Integer ordemExibicao,

        @Schema(
            description = "Status de atividade da categoria",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O status ativo é obrigatório.")
        Boolean ativo,

        @Schema(
            description = "URL da imagem representativa da categoria",
            example = "https://example.com/images/categoria-lanches.jpg"
        )
        @Size(max = 255, message = "O link da imagem não pode ultrapassar 255 caracteres.")
        String urlImagem
) {
    public CategoriaRequestDTO {
        if (nome != null) nome = nome.trim().toUpperCase();
        if (ordemExibicao == null) ordemExibicao = 0;
        if (ativo == null) ativo = true;
    }
}