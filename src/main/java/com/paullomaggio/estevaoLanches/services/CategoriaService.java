package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CategoriaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CategoriaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.repositories.CategoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listarTodas() {
        // Agora busca trazendo na ordem correta de exibição configurada pelo usuário
        return categoriaRepository.findAllByOrderByOrdemExibicaoAsc().stream()
                .map(CategoriaResponseDTO::new)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 1. BUSCAR CATEGORIA PELO NOME (Lupinha do Front)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> buscarPorNome(String nome) {
        return categoriaRepository.buscarPorNome(nome).stream()
                .map(CategoriaResponseDTO::new)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 2. BUSCAR POR ID
    // =========================================================================
    @Transactional(readOnly = true)
    public CategoriaResponseDTO buscarPorId(UUID id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada com o ID informado."));
        return new CategoriaResponseDTO(categoria);
    }

    @Transactional
    public CategoriaResponseDTO salvar(CategoriaRequestDTO dto) {
        Categoria categoria = new Categoria();
        copiarDtoParaEntidade(dto, categoria);

        Categoria categoriaSalva = categoriaRepository.save(categoria);
        return new CategoriaResponseDTO(categoriaSalva);
    }

    // =========================================================================
    // 3. EDITAR / ATUALIZAR CATEGORIA
    // =========================================================================
    @Transactional
    public CategoriaResponseDTO atualizar(UUID id, CategoriaRequestDTO dto) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Não é possível editar. Categoria não encontrada!"));

        copiarDtoParaEntidade(dto, categoria);

        Categoria categoriaAtualizada = categoriaRepository.save(categoria);
        return new CategoriaResponseDTO(categoriaAtualizada);
    }

    // =========================================================================
    // 4. EXCLUIR CATEGORIA DEFINITIVAMENTE
    // =========================================================================
    @Transactional
    public void deletar(UUID id) {
        if (!categoriaRepository.existsById(id)) {
            throw new RuntimeException("Não é possível excluir. Categoria não encontrada!");
        }
        categoriaRepository.deleteById(id);
    }

    // Método auxiliar privado para mapeamento de campos
    private void copiarDtoParaEntidade(CategoriaRequestDTO dto, Categoria categoria) {
        categoria.setNome(dto.nome());
        categoria.setDescricao(dto.descricao());
        categoria.setUrlImagem(dto.urlImagem());
        categoria.setOrdemExibicao(dto.ordemExibicao() != null ? dto.ordemExibicao() : 0);
        categoria.setAtivo(dto.ativo() != null ? dto.ativo() : true);
    }
}