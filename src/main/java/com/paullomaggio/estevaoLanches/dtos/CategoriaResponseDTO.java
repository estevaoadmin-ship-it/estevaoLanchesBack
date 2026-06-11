package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Categoria;
import java.util.UUID;

public record CategoriaResponseDTO(
        UUID id,
        String nome,
        String descricao,
        Integer ordemExibicao,
        Boolean ativo
) {
    public CategoriaResponseDTO(Categoria categoria) {
        this(
                categoria.getId(),
                categoria.getNome(),
                categoria.getDescricao(),
                categoria.getOrdemExibicao(),
                categoria.getAtivo()
        );
    }
}