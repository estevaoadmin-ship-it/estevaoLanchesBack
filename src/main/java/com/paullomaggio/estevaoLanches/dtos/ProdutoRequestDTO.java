package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProdutoRequestDTO(
        @NotBlank(message = "O nome do produto é obrigatório e não pode conter apenas espaços.")
        @Size(max = 150, message = "O nome do produto deve ter no máximo 150 caracteres.")
        String nome,

        @NotBlank(message = "A descrição do produto é obrigatória.")
        String descricao,

        @NotNull(message = "O preço do produto é obrigatório.")
        @DecimalMin(value = "0.01", message = "O preço do produto deve ser de pelo menos R$ 0.01.")
        @Digits(integer = 8, fraction = 2, message = "O preço deve estar no formato correto de milhar e dois centavos.")
        BigDecimal preco,

        @Size(max = 255, message = "O link da imagem não pode ultrapassar 255 caracteres.")
        String urlImagem,

        @NotNull(message = "O status do produto (DISPONIVEL/INDISPONIVEL) é obrigatório.")
        StatusProduto status,

        @NotNull(message = "Informe se o produto é um combo ou não.")
        Boolean isCombo,

        @NotNull(message = "Informe se o produto necessita de preparo operacional na cozinha.")
        Boolean precisaPreparo,

        @NotNull(message = "O ID da categoria é obrigatório para vincular o produto.")
        UUID categoriaId,

        List<UUID> adicionaisIds
) {
        public ProdutoRequestDTO {
                if (nome != null) nome = nome.trim().toUpperCase();
                if (isCombo == null) isCombo = false;
                if (precisaPreparo == null) precisaPreparo = true;
        }
}