package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import com.paullomaggio.estevaoLanches.repositories.CategoriaRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private AdicionalRepository adicionalRepository;

    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> listarTodos() {
        return produtoRepository.findAll().stream()
                .map(ProdutoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> buscarPorTermo(String termo) {
        return produtoRepository.buscarPorTermo(termo).stream()
                .map(ProdutoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProdutoResponseDTO buscarPorId(UUID id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com o ID informado."));
        return new ProdutoResponseDTO(produto);
    }

    @Transactional
    public ProdutoResponseDTO salvar(ProdutoRequestDTO dto) {
        Produto produto = new Produto();
        copiarDtoParaEntidade(dto, produto);

        Produto produtoGuardado = produtoRepository.save(produto);
        return new ProdutoResponseDTO(produtoGuardado);
    }

    @Transactional
    public ProdutoResponseDTO atualizar(UUID id, ProdutoRequestDTO dto) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Não é possível editar. Produto não encontrado!"));

        copiarDtoParaEntidade(dto, produto);

        Produto produtoAtualizado = produtoRepository.save(produto);
        return new ProdutoResponseDTO(produtoAtualizado);
    }

    @Transactional
    public void deletar(UUID id) {
        if (!produtoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Não é possível excluir. Produto não encontrado!");
        }
        produtoRepository.deleteById(id);
    }

    private void copiarDtoParaEntidade(ProdutoRequestDTO dto, Produto produto) {
        produto.setNome(dto.nome());
        produto.setDescricao(dto.descricao());
        produto.setPreco(dto.preco());
        produto.setUrlImagem(dto.urlImagem());
        produto.setStatus(dto.status());
        produto.setIsCombo(dto.isCombo());
        produto.setPrecisaPreparo(dto.precisaPreparo()); // 🚀 CORRIGIDO: Vincula a flag à entidade persistida

        // Vincula a Categoria
        Categoria categoria = categoriaRepository.findById(dto.categoriaId())
                .orElseThrow(() -> new BusinessRuleException("A Categoria informada para o produto não existe!"));
        produto.setCategoria(categoria);

        // Vincula os Adicionais
        if (dto.adicionaisIds() != null && !dto.adicionaisIds().isEmpty()) {
            List<Adicional> adicionais = adicionalRepository.findAllById(dto.adicionaisIds());
            produto.setAdicionais(adicionais);
        } else {
            produto.getAdicionais().clear();
        }
    }
}