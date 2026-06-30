package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "DTO para criação ou atualização de um produto")
public record ProdutoRequestDTO(
        @Schema(
            description = "Nome do produto",
            example = "Hambúrguer Clássico",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "O nome do produto é obrigatório e não pode conter apenas espaços.")
        @Size(max = 150, message = "O nome do produto deve ter no máximo 150 caracteres.")
        String nome,

        @Schema(
            description = "Descrição detalhada do produto",
            example = "Delicioso hambúrguer com carne bovina, queijo, alface, tomate e molho especial."
        )
        @NotBlank(message = "A descrição do produto é obrigatória.")
        String descricao,

        @Schema(
            description = "Preço do produto",
            example = "29.90",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O preço do produto é obrigatório.")
        @DecimalMin(value = "0.01", message = "O preço do produto deve ser de pelo menos R$ 0.01.")
        @Digits(integer = 8, fraction = 2, message = "O preço deve estar no formato correto de milhar e dois centavos.")
        BigDecimal preco,

        @Schema(
            description = "URL da imagem do produto",
            example = "https://example.com/images/hamburguer-classico.jpg"
        )
        @Size(max = 255, message = "O link da imagem não pode ultrapassar 255 caracteres.")
        String urlImagem,

        @Schema(
            description = "Status de disponibilidade do produto",
            allowableValues = {"DISPONIVEL", "INDISPONIVEL"},
            example = "DISPONIVEL",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O status do produto (DISPONIVEL/INDISPONIVEL) é obrigatório.")
        StatusProduto status,

        @Schema(
            description = "Indica se o produto é um combo (conjunto de outros produtos)",
            example = "false",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Informe se o produto é um combo ou não.")
        Boolean isCombo,

        @Schema(
            description = "Indica se o produto necessita de preparo na cozinha (ex: hambúrguer vs. refrigerante)",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Informe se o produto necessita de preparo operacional na cozinha.")
        Boolean precisaPreparo,

        @Schema(
            description = "ID da categoria à qual o produto pertence",
            format = "uuid",
            example = "1a2b3c4d-5e6f-7890-1234-567890abcdef",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O ID da categoria é obrigatório para vincular o produto.")
        UUID categoriaId,

        @Schema(
            description = "Lista de IDs de adicionais que podem ser aplicados a este produto",
            type = "array",
            example = "[\"f1e2d3c4-b5a6-0987-6543-210fedcba987\", \"a1b2c3d4-e5f6-7890-1234-567890abcdef\"]"
        )
        List<UUID> adicionaisIds
) {
        public ProdutoRequestDTO {
                if (nome != null) nome = nome.trim().toUpperCase();
                if (isCombo == null) isCombo = false;
                if (precisaPreparo == null) precisaPreparo = true;
        }
}