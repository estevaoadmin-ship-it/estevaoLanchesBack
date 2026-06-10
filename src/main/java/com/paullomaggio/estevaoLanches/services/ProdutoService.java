package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import com.paullomaggio.estevaoLanches.repositories.CategoriaRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private AdicionalRepository adicionalRepository;

    public List<ProdutoResponseDTO> listarTodos() {
        // Vai buscar todos os produtos à base de dados e converte cada um num ProdutoResponseDTO
        return produtoRepository.findAll().stream()
                .map(ProdutoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProdutoResponseDTO salvar(ProdutoRequestDTO dto) {
        Produto produto = new Produto();
        produto.setNome(dto.nome());
        produto.setDescricao(dto.descricao());
        produto.setPreco(dto.preco());
        produto.setUrlImagem(dto.urlImagem());
        produto.setStatus(dto.status());
        produto.setIsCombo(dto.isCombo());

        // 1. Vai buscar a Categoria através do ID enviado no DTO
        Categoria categoria = categoriaRepository.findById(dto.categoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada!"));
        produto.setCategoria(categoria);

        // 2. Se vierem IDs de adicionais, vai buscá-los à base de dados
        if (dto.adicionaisIds() != null && !dto.adicionaisIds().isEmpty()) {
            List<Adicional> adicionais = adicionalRepository.findAllById(dto.adicionaisIds());
            produto.setAdicionais(adicionais);
        }

        // 3. Guarda a Entidade na base de dados
        Produto produtoGuardado = produtoRepository.save(produto);

        // 4. Converte a Entidade guardada num DTO e devolve
        return new ProdutoResponseDTO(produtoGuardado);
    }
}