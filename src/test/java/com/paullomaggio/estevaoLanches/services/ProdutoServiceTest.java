package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import com.paullomaggio.estevaoLanches.repositories.CategoriaRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @InjectMocks
    private ProdutoService produtoService;

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private AdicionalRepository adicionalRepository;

    private Categoria categoriaMock;
    private Produto produtoMock;
    private ProdutoRequestDTO requestDTOMock;
    private UUID categoriaId = UUID.randomUUID();
    private UUID produtoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoriaMock = new Categoria();
        categoriaMock.setId(categoriaId);
        categoriaMock.setNome("Lanches");

        produtoMock = new Produto();
        produtoMock.setId(produtoId);
        produtoMock.setNome("X-Bacon Especial");
        produtoMock.setDescricao("Hambúrguer artesanal");
        produtoMock.setPreco(new BigDecimal("25.00"));
        produtoMock.setStatus(StatusProduto.DISPONIVEL);
        produtoMock.setIsCombo(false);
        produtoMock.setCategoria(categoriaMock);
        produtoMock.setAdicionais(new ArrayList<>());
    }

    // =========================================================================
    // TESTES DE CRIAÇÃO (SALVAR)
    // =========================================================================

    @Test
    @DisplayName("Deve salvar produto com categoria e sem adicionais com sucesso")
    void deveSalvarProdutoSemAdicionais() {
        requestDTOMock = new ProdutoRequestDTO("X-Bacon Especial", "Hambúrguer", new BigDecimal("25.00"), "", StatusProduto.DISPONIVEL, false, categoriaId, null);

        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

        ProdutoResponseDTO response = produtoService.salvar(requestDTOMock);

        assertNotNull(response);
        assertEquals("X-Bacon Especial", response.nome());
        verify(adicionalRepository, never()).findAllById(any());
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar salvar produto com categoria inexistente")
    void deveLancarExcecaoCategoriaInexistente() {
        requestDTOMock = new ProdutoRequestDTO("Erro", "Erro", BigDecimal.TEN, "", StatusProduto.DISPONIVEL, false, UUID.randomUUID(), null);

        when(categoriaRepository.findById(any())).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> produtoService.salvar(requestDTOMock));
        assertEquals("Categoria informada não existe!", exception.getMessage());
    }

    @Test
    @DisplayName("Deve salvar produto associando os adicionais corretamente")
    void deveSalvarProdutoComAdicionais() {
        UUID adicionalId = UUID.randomUUID();
        requestDTOMock = new ProdutoRequestDTO("Lanche", "Desc", BigDecimal.TEN, "", StatusProduto.DISPONIVEL, false, categoriaId, List.of(adicionalId));

        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
        when(adicionalRepository.findAllById(any())).thenReturn(List.of(new Adicional()));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

        produtoService.salvar(requestDTOMock);

        verify(adicionalRepository, times(1)).findAllById(anyList());
    }

    // =========================================================================
    // TESTES DE BUSCA (READ)
    // =========================================================================

    @Test
    @DisplayName("Deve listar todos os produtos e converter para DTO")
    void deveListarTodos() {
        when(produtoRepository.findAll()).thenReturn(List.of(produtoMock));

        List<ProdutoResponseDTO> resultados = produtoService.listarTodos();

        assertEquals(1, resultados.size());
        assertEquals("X-Bacon Especial", resultados.get(0).nome());
    }

    @Test
    @DisplayName("Deve retornar produto quando buscar por ID existente")
    void deveBuscarPorIdExistente() {
        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoMock));

        ProdutoResponseDTO response = produtoService.buscarPorId(produtoId);

        assertNotNull(response);
        assertEquals(produtoId, response.id());
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar ID que não existe")
    void deveLancarExcecaoIdInexistente() {
        when(produtoRepository.findById(any())).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> produtoService.buscarPorId(UUID.randomUUID()));
        assertEquals("Produto não encontrado com o ID informado.", exception.getMessage());
    }

    // =========================================================================
    // TESTES DE ATUALIZAÇÃO (UPDATE)
    // =========================================================================

    @Test
    @DisplayName("Deve atualizar as informações do produto")
    void deveAtualizarProduto() {
        requestDTOMock = new ProdutoRequestDTO("Nome Novo", "Desc Nova", BigDecimal.ONE, "", StatusProduto.DISPONIVEL, false, categoriaId, new ArrayList<>());

        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoMock));
        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

        produtoService.atualizar(produtoId, requestDTOMock);

        verify(produtoRepository).save(argThat(p -> p.getNome().equals("Nome Novo") && p.getAdicionais().isEmpty()));
    }

    // =========================================================================
    // TESTES DE EXCLUSÃO (DELETE)
    // =========================================================================

    @Test
    @DisplayName("Deve deletar produto quando ele existir")
    void deveDeletarProduto() {
        when(produtoRepository.existsById(produtoId)).thenReturn(true);

        produtoService.deletar(produtoId);

        verify(produtoRepository, times(1)).deleteById(produtoId);
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar deletar produto que não existe")
    void deveLancarExcecaoDeletarInexistente() {
        when(produtoRepository.existsById(produtoId)).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> produtoService.deletar(produtoId));
        assertEquals("Não é possível excluir. Produto não encontrado!", exception.getMessage());
        verify(produtoRepository, never()).deleteById(any());
    }
}