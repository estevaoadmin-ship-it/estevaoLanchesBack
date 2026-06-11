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

    // =========================================================================
    // 1. BUSCAR POR TERMO (Lupinha integrada ao banco de dados)
    // =========================================================================
    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> buscarPorTermo(String termo) {
        return produtoRepository.buscarPorTermo(termo).stream()
                .map(ProdutoResponseDTO::new)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 2. BUSCAR POR ID (Útil para carregar dados antes de uma edição)
    // =========================================================================
    @Transactional(readOnly = true)
    public ProdutoResponseDTO buscarPorId(UUID id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID informado."));
        return new ProdutoResponseDTO(produto);
    }

    @Transactional
    public ProdutoResponseDTO salvar(ProdutoRequestDTO dto) {
        Produto produto = new Produto();
        copiarDtoParaEntidade(dto, produto);

        Produto produtoGuardado = produtoRepository.save(produto);
        return new ProdutoResponseDTO(produtoGuardado);
    }

    // =========================================================================
    // 3. EDITAR / ATUALIZAR PRODUTO EXISTENTE
    // =========================================================================
    @Transactional
    public ProdutoResponseDTO atualizar(UUID id, ProdutoRequestDTO dto) {
        // Busca o registro atual na base de dados
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Não é possível editar. Produto não encontrado!"));

        // Atualiza os dados da entidade com as novas informações vindas do DTO
        copiarDtoParaEntidade(dto, produto);

        // Salva as alterações mescladas
        Produto produtoAtualizado = produtoRepository.save(produto);
        return new ProdutoResponseDTO(produtoAtualizado);
    }

    // =========================================================================
    // 4. EXCLUIR PRODUTO DEFINITIVAMENTE
    // =========================================================================
    @Transactional
    public void deletar(UUID id) {
        if (!produtoRepository.existsById(id)) {
            throw new RuntimeException("Não é possível excluir. Produto não encontrado!");
        }
        produtoRepository.deleteById(id);
    }

    // Métodozinho privado auxiliar para reaproveitar código e não repetir set's no Salvar e Editar
    private void copiarDtoParaEntidade(ProdutoRequestDTO dto, Produto produto) {
        produto.setNome(dto.nome());
        produto.setDescricao(dto.descricao());
        produto.setPreco(dto.preco());
        produto.setUrlImagem(dto.urlImagem());
        produto.setStatus(dto.status());
        produto.setIsCombo(dto.isCombo());

        // Vincula a Categoria
        Categoria categoria = categoriaRepository.findById(dto.categoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria informada não existe!"));
        produto.setCategoria(categoria);

        // Vincula os Adicionais (Limpa os antigos e adiciona os novos se houver)
        if (dto.adicionaisIds() != null && !dto.adicionaisIds().isEmpty()) {
            List<Adicional> adicionais = adicionalRepository.findAllById(dto.adicionaisIds());
            produto.setAdicionais(adicionais);
        } else {
            produto.getAdicionais().clear();
        }
    }
}