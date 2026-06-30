package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Schema(description = "Resposta contendo os detalhes de um produto")
public record ProdutoResponseDTO(
        @Schema(
            description = "Identificador único do produto",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID id,
        @Schema(
            description = "Nome do produto",
            example = "Hambúrguer Clássico"
        )
        String nome,
        @Schema(
            description = "Descrição detalhada do produto",
            example = "Delicioso hambúrguer com carne bovina, queijo, alface, tomate e molho especial."
        )
        String descricao,
        @Schema(
            description = "Preço do produto",
            example = "29.90"
        )
        BigDecimal preco,
        @Schema(
            description = "URL da imagem do produto",
            example = "https://example.com/images/hamburguer-classico.jpg"
        )
        String urlImagem,
        @Schema(
            description = "Status de disponibilidade do produto",
            allowableValues = {"DISPONIVEL", "INDISPONIVEL"},
            example = "DISPONIVEL"
        )
        StatusProduto status,
        @Schema(
            description = "Indica se o produto é um combo (conjunto de outros produtos)",
            example = "false"
        )
        Boolean isCombo,
        @Schema(
            description = "Indica se o produto necessita de preparo na cozinha",
            example = "true"
        )
        Boolean precisaPreparo,
        @Schema(
            description = "ID da categoria à qual o produto pertence",
            format = "uuid",
            example = "1a2b3c4d-5e6f-7890-1234-567890abcdef"
        )
        UUID categoriaId,
        @Schema(
            description = "Nome da categoria à qual o produto pertence",
            example = "Lanches"
        )
        String categoriaNome,
        @Schema(description = "Lista de adicionais que podem ser aplicados a este produto")
        List<AdicionalResponseDTO> adicionais
) {
    public ProdutoResponseDTO(Produto produto) {
        this(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getPreco(),
                produto.getUrlImagem(),
                produto.getStatus(),
                produto.getIsCombo(),
                produto.getPrecisaPreparo(),
                produto.getCategoria() != null ? produto.getCategoria().getId() : null,
                produto.getCategoria() != null ? produto.getCategoria().getNome() : null,
                produto.getAdicionais() != null ?
                        produto.getAdicionais().stream()
                                .map(AdicionalResponseDTO::new)
                                .collect(Collectors.toList())
                        : List.of()
        );
    }
}