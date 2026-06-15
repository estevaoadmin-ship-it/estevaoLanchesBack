package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ProdutoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ProdutoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.Categoria;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.argThat; // 🚀 Garantido import para matchers de argumento
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
        produtoMock.setPrecisaPreparo(true); // Padrão de lanche
        produtoMock.setCategoria(categoriaMock);
        produtoMock.setAdicionais(new ArrayList<>());
    }

    // =========================================================================
    // SEÇÃO 1: TESTES DE CRIAÇÃO (SALVAR) - AJUSTADOS
    // =========================================================================

    @Test
    @DisplayName("Deve salvar produto com categoria e sem adicionais com sucesso")
    void deveSalvarProdutoSemAdicionais() {
        // 🚀 AJUSTADO: Adicionado 'true' no construtor do DTO
        requestDTOMock = new ProdutoRequestDTO("X-Bacon Especial", "Hambúrguer", new BigDecimal("25.00"), "", StatusProduto.DISPONIVEL, false, true, categoriaId, null);

        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

        ProdutoResponseDTO response = produtoService.salvar(requestDTOMock);

        assertNotNull(response);
        assertEquals("X-Bacon Especial", response.nome());
        verify(adicionalRepository, never()).findAllById(any());
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar exceção de negócio ao tentar salvar produto com categoria inexistente")
    void deveLancarExcecaoCategoriaInexistente() {
        // 🚀 AJUSTADO: Adicionado 'true' no construtor do DTO
        requestDTOMock = new ProdutoRequestDTO("Erro", "Erro", BigDecimal.TEN, "", StatusProduto.DISPONIVEL, false, true, UUID.randomUUID(), null);

        when(categoriaRepository.findById(any())).thenReturn(Optional.empty());

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> produtoService.salvar(requestDTOMock));
        assertEquals("A Categoria informada para o produto não existe!", exception.getMessage());
    }

    @Test
    @DisplayName("Deve salvar produto associando os adicionais corretamente")
    void deveSalvarProdutoComAdicionais() {
        UUID adicionalId = UUID.randomUUID();
        // 🚀 AJUSTADO: Adicionado 'true' no construtor do DTO
        requestDTOMock = new ProdutoRequestDTO("Lanche", "Desc", BigDecimal.TEN, "", StatusProduto.DISPONIVEL, false, true, categoriaId, List.of(adicionalId));

        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
        when(adicionalRepository.findAllById(any())).thenReturn(List.of(new Adicional()));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

        produtoService.salvar(requestDTOMock);

        verify(adicionalRepository, times(1)).findAllById(anyList());
    }

    // =========================================================================
    // 🚀 NOVA SEÇÃO: VALIDAÇÃO DO FLUXO OPERACIONAL (COZINHA VS BALCÃO)
    // =========================================================================

    @Test
    @DisplayName("CT-PROD-KPI-001: Deve repassar 'precisaPreparo = true' para a entidade ao salvar um lanche")
    void devePersistirProdutoQuePrecisaDePreparo() {
        requestDTOMock = new ProdutoRequestDTO("Burger Brutal", "Artesanal", new BigDecimal("32.00"), "", StatusProduto.DISPONIVEL, false, true, categoriaId, null);

        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0)); // Retorna a própria entidade gerada

        ProdutoResponseDTO response = produtoService.salvar(requestDTOMock);

        assertNotNull(response);
        verify(produtoRepository).save(argThat(Produto::getPrecisaPreparo)); // Valida se o setter recebeu true
    }

    @Test
    @DisplayName("CT-PROD-KPI-002: Deve repassar 'precisaPreparo = false' para a entidade ao salvar uma bebida/produto pronto")
    void devePersistirProdutoDiretoDeBalcaoSemPreparo() { // 🚀 CORRIGIDO: Nome do método unificado
        requestDTOMock = new ProdutoRequestDTO("Fanta Laranja", "Lata", new BigDecimal("5.50"), "", StatusProduto.DISPONIVEL, false, false, categoriaId, null);

        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        produtoService.salvar(requestDTOMock);

        verify(produtoRepository).save(argThat(p -> !p.getPrecisaPreparo())); // Valida se o setter recebeu false
    }

    // =========================================================================
    // SEÇÃO 2: TESTES DE BUSCA (READ) - INTACTOS
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
    @DisplayName("Deve lançar ResourceNotFoundException ao buscar ID que não existe")
    void deveLancarExcecaoIdInexistente() {
        when(produtoRepository.findById(any())).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> produtoService.buscarPorId(UUID.randomUUID()));
        assertEquals("Produto não encontrado com o ID informado.", exception.getMessage());
    }

    // =========================================================================
    // SEÇÃO 3: TESTES DE ATUALIZAÇÃO (UPDATE) - AJUSTADOS
    // =========================================================================

    @Test
    @DisplayName("Deve atualizar as informações do produto e alterar fluxo operacional se necessário")
    void deveAtualizarProduto() {
        // 🚀 AJUSTADO: Adicionado 'false' no DTO simulando uma alteração de destino operacional (ex: transformando em item de balcão)
        requestDTOMock = new ProdutoRequestDTO("Nome Novo", "Desc Nova", BigDecimal.ONE, "", StatusProduto.DISPONIVEL, false, false, categoriaId, new ArrayList<>());

        when(produtoRepository.findById(produtoId)).thenReturn(Optional.of(produtoMock));
        when(categoriaRepository.findById(categoriaId)).thenReturn(Optional.of(categoriaMock));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoMock);

        produtoService.atualizar(produtoId, requestDTOMock);

        // Verifica se os dados novos e a flag redefinida para FALSE foram mapeados com sucesso
        verify(produtoRepository).save(argThat(p ->
                p.getNome().equals("Nome Novo") &&
                        p.getAdicionais().isEmpty() &&
                        !p.getPrecisaPreparo()
        ));
    }

    // =========================================================================
    // SEÇÃO 4: TESTES DE EXCLUSÃO (DELETE) - INTACTOS
    // =========================================================================

    @Test
    @DisplayName("Deve deletar produto quando ele existir")
    void deveDeletarProduto() {
        when(produtoRepository.existsById(produtoId)).thenReturn(true);

        produtoService.deletar(produtoId);

        verify(produtoRepository, times(1)).deleteById(produtoId);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar deletar produto que não existe")
    void deveLancarExcecaoDeletarInexistente() {
        when(produtoRepository.existsById(produtoId)).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> produtoService.deletar(produtoId));
        assertEquals("Não é possível excluir. Produto não encontrado!", exception.getMessage());
        verify(produtoRepository, never()).deleteById(any());
    }
}