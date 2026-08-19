package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ItemComposicaoRequestDTO;
import com.paullomaggio.estevaoLanches.entities.ComboProduto;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusProduto;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ComboProdutoRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suite de Testes — Sincronização da Composição de ComboProduto")
class ComboProdutoServiceComposicaoTest {

    @Mock
    private ComboProdutoRepository comboProdutoRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    private ComboProdutoService comboProdutoService;

    private UUID comboId;
    private UUID produtoAId;
    private UUID produtoBId;
    private UUID produtoCId;

    private Produto comboMock;
    private Produto produtoAMock;
    private Produto produtoBMock;
    private Produto produtoCMock;

    @BeforeEach
    void setUp() {
        comboProdutoService = new ComboProdutoService(comboProdutoRepository, produtoRepository);

        comboId = UUID.randomUUID();
        produtoAId = UUID.randomUUID();
        produtoBId = UUID.randomUUID();
        produtoCId = UUID.randomUUID();

        comboMock = new Produto();
        comboMock.setId(comboId);
        comboMock.setNome("COMBO TEST");
        comboMock.setStatus(StatusProduto.DISPONIVEL);

        produtoAMock = new Produto();
        produtoAMock.setId(produtoAId);
        produtoAMock.setNome("PRODUTO A");
        produtoAMock.setStatus(StatusProduto.DISPONIVEL);

        produtoBMock = new Produto();
        produtoBMock.setId(produtoBId);
        produtoBMock.setNome("PRODUTO B");
        produtoBMock.setStatus(StatusProduto.DISPONIVEL);

        produtoCMock = new Produto();
        produtoCMock.setId(produtoCId);
        produtoCMock.setNome("PRODUTO C");
        produtoCMock.setStatus(StatusProduto.DISPONIVEL);
    }

    // Helper to create a ComboProduto with given ids and quantity
    private ComboProduto makeVinculo(UUID vinculoId, UUID produtoId, int quantidade) {
        ComboProduto cp = new ComboProduto();
        cp.setId(vinculoId);
        cp.setCombo(comboMock);
        Produto produto = new Produto();
        produto.setId(produtoId);
        produto.setNome("PRODUTO");
        produto.setStatus(StatusProduto.DISPONIVEL);
        cp.setProduto(produto);
        cp.setQuantidade(quantidade);
        return cp;
    }
@Nested
    @DisplayName("Cenários de Sincronização")
    class Composicao {

        @Test
        @DisplayName("CT-SYNC-01: Remove duplicados históricos – A B B → A B")
        void testeRemoveDuplicadosHistoricos() {
            // Estado inicial: A, B, B (duplicado de B)
            UUID vinculoAId = UUID.randomUUID();
            UUID vinculoBId1 = UUID.randomUUID();
            UUID vinculoBId2 = UUID.randomUUID();

            ComboProduto vinculoA = makeVinculo(vinculoAId, produtoAId, 1);
            ComboProduto vinculoB1 = makeVinculo(vinculoBId1, produtoBId, 1);
            ComboProduto vinculoB2 = makeVinculo(vinculoBId2, produtoBId, 1);

            List<ComboProduto> associacoesIniciais = Arrays.asList(vinculoA, vinculoB1, vinculoB2);

            // Request: A B (quantidades 1)
            List<ItemComposicaoRequestDTO> novosItens = Arrays.asList(
                    new ItemComposicaoRequestDTO(produtoAId, 1),
                    new ItemComposicaoRequestDTO(produtoBId, 1)
            );

            when(produtoRepository.findById(comboId)).thenReturn(Optional.of(comboMock));
            when(produtoRepository.findById(produtoAId)).thenReturn(Optional.of(produtoAMock));
            when(produtoRepository.findById(produtoBId)).thenReturn(Optional.of(produtoBMock));
            
            when(comboProdutoRepository.findByComboId(comboId)).thenReturn(associacoesIniciais);

            // Execute
            comboProdutoService.atualizarComposicaoDoCombo(comboId, novosItens);

            // Verify deletions: one of the B duplicates should be removed
            ArgumentCaptor<List<ComboProduto>> deleteCaptor = ArgumentCaptor.forClass(List.class);
            verify(comboProdutoRepository).deleteAll(deleteCaptor.capture());
            List<ComboProduto> deleted = deleteCaptor.getValue();
            assertEquals(1, deleted.size(), "Deveria remover exatamente um registro de B duplicado");
            assertTrue(deleted.stream().anyMatch(cp -> cp.getId().equals(vinculoBId1) || cp.getId().equals(vinculoBId2)),
                    "Registro removido deve ser um dos duplicados de B");

            // Verify saves: A and one B should be saved (maybe updated)
            ArgumentCaptor<List<ComboProduto>> saveCaptor = ArgumentCaptor.forClass(List.class);
            verify(comboProdutoRepository, times(1)).saveAll(saveCaptor.capture());
            List<List<ComboProduto>> savedLists = saveCaptor.getAllValues();
            // Flatten to check content
            List<ComboProduto> savedAll = savedLists.stream().flatMap(List::stream).collect(Collectors.toList());
            assertEquals(2, savedAll.size(), "Deveria salvar exatamente dois registros (A e um B)");
            Set<UUID> savedIds = savedAll.stream().map(ComboProduto::getId).collect(Collectors.toSet());
            assertTrue(savedIds.contains(vinculoAId), "Vinculo A deveria ser preservado");
            assertTrue(savedIds.contains(vinculoBId1) || savedIds.contains(vinculoBId2),
                    "Um dos vinculos de B deveria ser preservado");

            // Verify that the preserved B has correct quantity (1)
            ComboProduto savedB = savedAll.stream()
                    .filter(cp -> cp.getProduto().getId().equals(produtoBId))
                    .findFirst()
                    .orElseThrow();
            assertEquals(1, savedB.getQuantidade(), "Quantidade do B preservado deve ser 1");
        }
@Test
        @DisplayName("CT-SYNC-02: Remove produto e duplicados – A B B C → A B")
        void testeRemoveProdutoEDuplicados() {
            // Estado inicial: A, B, B, C
            UUID vinculoAId = UUID.randomUUID();
            UUID vinculoBId1 = UUID.randomUUID();
            UUID vinculoBId2 = UUID.randomUUID();
            UUID vinculoCId = UUID.randomUUID();

            ComboProduto vinculoA = makeVinculo(vinculoAId, produtoAId, 1);
            ComboProduto vinculoB1 = makeVinculo(vinculoBId1, produtoBId, 1);
            ComboProduto vinculoB2 = makeVinculo(vinculoBId2, produtoBId, 1);
            ComboProduto vinculoC = makeVinculo(vinculoCId, produtoCId, 1);

            List<ComboProduto> associacoesIniciais = Arrays.asList(vinculoA, vinculoB1, vinculoB2, vinculoC);

            // Request: A B
            List<ItemComposicaoRequestDTO> novosItens = Arrays.asList(
                    new ItemComposicaoRequestDTO(produtoAId, 1),
                    new ItemComposicaoRequestDTO(produtoBId, 1)
            );

            when(produtoRepository.findById(comboId)).thenReturn(Optional.of(comboMock));
            when(produtoRepository.findById(produtoAId)).thenReturn(Optional.of(produtoAMock));
            when(produtoRepository.findById(produtoBId)).thenReturn(Optional.of(produtoBMock));
            
            when(comboProdutoRepository.findByComboId(comboId)).thenReturn(associacoesIniciais);

            comboProdutoService.atualizarComposicaoDoCombo(comboId, novosItens);

            // Verify deletions: C and one B duplicate should be removed
            ArgumentCaptor<List<ComboProduto>> deleteCaptor = ArgumentCaptor.forClass(List.class);
            verify(comboProdutoRepository).deleteAll(deleteCaptor.capture());
            List<ComboProduto> deleted = deleteCaptor.getValue();
            assertEquals(2, deleted.size(), "Deveria remover exatamente dois registros (C e um B duplicado)");
            Set<UUID> deletedIds = deleted.stream().map(ComboProduto::getId).collect(Collectors.toSet());
            assertTrue(deletedIds.contains(vinculoCId), "C deveria ser removido");
            assertTrue(deletedIds.contains(vinculoBId1) || deletedIds.contains(vinculoBId2),
                    "Um dos duplicados de B deveria ser removido");

            // Verify saves: A and one B should be saved
            ArgumentCaptor<List<ComboProduto>> saveCaptor = ArgumentCaptor.forClass(List.class);
            verify(comboProdutoRepository, times(1)).saveAll(saveCaptor.capture());
            List<List<ComboProduto>> savedLists = saveCaptor.getAllValues();
            List<ComboProduto> savedAll = savedLists.stream().flatMap(List::stream).collect(Collectors.toList());
            assertEquals(2, savedAll.size(), "Deveria salvar exatamente dois registros (A e um B)");
            Set<UUID> savedIds = savedAll.stream().map(ComboProduto::getId).collect(Collectors.toSet());
            assertTrue(savedIds.contains(vinculoAId), "A deveria ser preservado");
            assertTrue(savedIds.contains(vinculoBId1) || savedIds.contains(vinculoBId2),
                    "Um dos B deveria ser preservado");

            // Verify quantities unchanged (1)
            savedAll.forEach(cp -> assertEquals(1, cp.getQuantidade(), "Quantidade deveria permanecer 1"));
        }
}
}

