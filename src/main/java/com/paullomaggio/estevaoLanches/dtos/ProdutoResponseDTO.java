package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record ProdutoResponseDTO(
        UUID id,
        String nome,
        String descricao,
        BigDecimal preco,
        String urlImagem,
        StatusProduto status,
        Boolean isCombo,
        Boolean precisaPreparo,
        UUID categoriaId,
        String categoriaNome,
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