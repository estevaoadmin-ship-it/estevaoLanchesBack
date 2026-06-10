package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CategoriaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CategoriaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.repositories.CategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    @Autowired
    private CategoriaRepository categoriaRepository;

    public List<CategoriaResponseDTO> listarTodas() {
        return categoriaRepository.findAll().stream()
                .map(CategoriaResponseDTO::new) // Transforma cada Entidade num DTO de resposta
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoriaResponseDTO salvar(CategoriaRequestDTO dto) {
        Categoria categoria = new Categoria();
        categoria.setNome(dto.nome());
        categoria.setDescricao(dto.descricao());
        categoria.setUrlImagem(dto.urlImagem());

        // Mantém os valores padrão caso o front-end não envie esses campos
        categoria.setOrdemExibicao(dto.ordemExibicao() != null ? dto.ordemExibicao() : 0);
        categoria.setAtivo(dto.ativo() != null ? dto.ativo() : true);

        Categoria categoriaSalva = categoriaRepository.save(categoria);
        return new CategoriaResponseDTO(categoriaSalva);
    }
}