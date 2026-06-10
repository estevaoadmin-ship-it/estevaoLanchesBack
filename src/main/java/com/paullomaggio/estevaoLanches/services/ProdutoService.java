package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Transactional(readOnly = true)
    public List<Produto> listarTodos() {
        return produtoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Produto> listarDisponiveis() {
        return produtoRepository.findByStatus(StatusProduto.DISPONIVEL);
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(UUID id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));
    }

    @Transactional
    public Produto salvar(Produto produto) {
        // Regra de Negócio: Validação básica de preço
        if (produto.getPreco() == null || produto.getPreco().doubleValue() <= 0) {
            throw new IllegalArgumentException("O produto deve ter um preço maior que zero.");
        }

        // Se for um novo produto e o status não foi definido, vira DISPONIVEL por padrão
        if (produto.getStatus() == null) {
            produto.setStatus(StatusProduto.DISPONIVEL);
        }

        return produtoRepository.save(produto);
    }

    @Transactional
    public void alterarStatus(UUID id, StatusProduto novoStatus) {
        Produto produto = buscarPorId(id);
        produto.setStatus(novoStatus);
        produtoRepository.save(produto); // O JPA atualiza automaticamente por estar na mesma transação
    }

    @Transactional
    public void deletar(UUID id) {
        Produto produto = buscarPorId(id);
        produtoRepository.delete(produto);
    }
}