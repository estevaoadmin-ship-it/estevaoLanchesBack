package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Categoria;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Resposta contendo os detalhes de uma categoria de produto")
public record CategoriaResponseDTO(
        @Schema(
            description = "Identificador único da categoria",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID id,
        @Schema(
            description = "Nome da categoria",
            example = "LANCHES"
        )
        String nome,
        @Schema(
            description = "Descrição da categoria",
            example = "Sanduíches e hambúrgueres diversos."
        )
        String descricao,
        @Schema(
            description = "Ordem de exibição da categoria na interface do usuário",
            example = "1"
        )
        Integer ordemExibicao,
        @Schema(
            description = "Status de atividade da categoria",
            example = "true"
        )
        Boolean ativo,
        @Schema(
            description = "URL da imagem representativa da categoria",
            example = "https://example.com/images/categoria-lanches.jpg"
        )
        String urlImagem
) {
    public CategoriaResponseDTO(Categoria categoria) {
        this(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                categoria.getOrdemExibicao(),
                categoria.getAtivo(),
                categoria.getUrlImagem()
        );
    }
}