package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Categoria;
import java.util.UUID;

// --- REQUEST ---
public record CategoriaRequestDTO(String nome, String descricao, Integer ordemExibicao, Boolean ativo, String urlImagem) {}