package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ProdutoRepositoryTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Categoria categoria;
    private Produto produto;

    @BeforeEach
    void setUp() {
        categoria = new Categoria();
        categoria.setNome("Lanches Especiais");
        categoria.setOrdemExibicao(1);
        categoria.setAtivo(true);
        categoriaRepository.save(categoria);

        produto = new Produto();
        produto.setNome("X-Bacon Artesanal");
        produto.setDescricao("Pão, carne, muito bacon e queijo");
        produto.setPreco(new BigDecimal("30.00"));
        produto.setStatus(StatusProduto.DISPONIVEL);
        produto.setIsCombo(false);
        produto.setCategoria(categoria);
        produtoRepository.save(produto);
    }

    @Test
    @DisplayName("Deve encontrar produto buscando parte do NOME")
    void buscarPorTermoNome() {
        List<Produto> resultados = produtoRepository.buscarPorTermo("bacon");
        assertFalse(resultados.isEmpty());
        assertEquals("X-Bacon Artesanal", resultados.get(0).getNome());
    }

    @Test
    @DisplayName("Deve encontrar produto buscando parte da DESCRIÇÃO")
    void buscarPorTermoDescricao() {
        List<Produto> resultados = produtoRepository.buscarPorTermo("muito bacon");
        assertFalse(resultados.isEmpty());
    }

    @Test
    @DisplayName("Deve encontrar produto buscando parte da CATEGORIA")
    void buscarPorTermoCategoria() {
        List<Produto> resultados = produtoRepository.buscarPorTermo("especiais");
        assertFalse(resultados.isEmpty());
        assertEquals(categoria.getId(), resultados.get(0).getCategoria().getId());
    }

    @Test
    @DisplayName("Deve ignorar maiúsculas e minúsculas na busca")
    void buscarPorTermoCaseInsensitive() {
        List<Produto> resultados = produtoRepository.buscarPorTermo("x-BACON");
        assertFalse(resultados.isEmpty());
    }

    @Test
    @DisplayName("Deve deletar todos os produtos de uma categoria específica")
    void deletarPorCategoriaId() {
        produtoRepository.deletarPorCategoriaId(categoria.getId());
        List<Produto> banco = produtoRepository.findAll();
        assertTrue(banco.isEmpty());
    }

    @Test
    @DisplayName("Deve buscar produtos pelo status DISPONIVEL")
    void findByStatus() {
        List<Produto> resultados = produtoRepository.findByStatus(StatusProduto.DISPONIVEL);
        assertEquals(1, resultados.size());
    }

    @Test
    @DisplayName("Deve filtrar apenas os produtos que não são combos")
    void findByIsComboFalse() {
        List<Produto> resultados = produtoRepository.findByIsComboFalse();
        assertEquals(1, resultados.size());
    }
}