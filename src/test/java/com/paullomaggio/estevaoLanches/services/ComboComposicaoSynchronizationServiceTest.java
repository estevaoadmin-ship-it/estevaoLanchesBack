package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ComboProdutoRequestDTO;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes — Sincronização da Composição do Combo")
class ComboComposicaoSynchronizationServiceTest {

    @Mock
    private ComboProdutoRepository comboProdutoRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    private ComboProdutoService comboProdutoService;

    private UUID comboId;
    private UUID produtoAId;
    private UUID produtoBId;
    private UUID produtoCId;
    private UUID produtoDId;

    private Produto comboMock;
    private Produto produtoAMock;
    private Produto produtoBMock;
    private Produto produtoCMock;
    private Produto produtoDMock;

    @BeforeEach
    void setUp() {
        comboProdutoService = new ComboProdutoService(comboProdutoRepository, produtoRepository);

        comboId = UUID.randomUUID();
        produtoAId = UUID.randomUUID();
        produtoBId = UUID.randomUUID();
        produtoCId = UUID.randomUUID();
        produtoDId = UUID.randomUUID();

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

        produtoDMock = new Produto();
        produtoDMock.setId(produtoDId);
        produtoDMock.setNome("PRODUTO D");
        produtoDMock.setStatus(StatusProduto.DISPONIVEL);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Converte o {@link Iterable} recebido pelos matchers do Mockito
     * (assinatura real de {@code deleteAll}/{@code saveAll} é {@code Iterable})
     * em uma {@link List} para permitir uso de size/get/stream.
     */
    private static List<ComboProduto> converterParaLista(Iterable<? extends ComboProduto> iteravel) {
        List<ComboProduto> lista = new ArrayList<>();
        iteravel.forEach(lista::add);
        return lista;
    }

    private List<ComboProduto> criarAssociacoesIniciais(Map<UUID, Integer> produtoIdParaQuantidade) {
        List<ComboProduto> associacoes = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : produtoIdParaQuantidade.entrySet()) {
            UUID pid = entry.getKey();
            Integer qtd = entry.getValue();
            Produto produto = new Produto();
            produto.setId(pid);
            produto.setNome("PRODUTO");
            produto.setStatus(StatusProduto.DISPONIVEL);
            ComboProduto cp = new ComboProduto();
            cp.setId(UUID.randomUUID());
            cp.setCombo(comboMock);
            cp.setProduto(produto);
            cp.setQuantidade(qtd);
            associacoes.add(cp);
        }
        return associacoes;
    }

    private ComboProduto criarVinculo(UUID idVinculo, Produto produto, Integer quantidade) {
        ComboProduto cp = new ComboProduto();
        cp.setId(idVinculo);
        cp.setCombo(comboMock);
        cp.setProduto(produto);
        cp.setQuantidade(quantidade);
        return cp;
    }

    private ItemComposicaoRequestDTO item(UUID produtoId, Integer quantidade) {
        return new ItemComposicaoRequestDTO(produtoId, quantidade);
    }

    private void stubProdutosEncontrados(Produto... produtos) {
        for (Produto p : produtos) {
            when(produtoRepository.findById(p.getId())).thenReturn(Optional.of(p));
        }
    }

    private Set<UUID> idsDosProdutos(List<ComboProduto> lista) {
        return lista.stream()
                .map(cp -> cp.getProduto().getId())
                .collect(Collectors.toSet());
    }

    // =========================================================================
    // CT-SYNC-01: REMOVER PRODUTO
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-01: Remover produto — estado A B C → nova composição A C")
    void testeRemoverProduto() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 1,
                        produtoCId, 1)));
        stubProdutosEncontrados(comboMock, produtoAMock, produtoCMock);

        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1)));

        // B é removido
        verify(comboProdutoRepository, times(1)).deleteAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoBId);
        }));

        // A e C são mantidos em UM único saveAll, sem recriação indevida
        verify(comboProdutoRepository, times(1)).saveAll(any());
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoCId));
        }));
    }

    // =========================================================================
    // CT-SYNC-02: COMPOSIÇÃO SEM ALTERAÇÃO
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-02: Composição sem alteração — A B C → A B C (sem duplicações)")
    void testeSemAlteracao() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 1,
                        produtoCId, 1)));
        stubProdutosEncontrados(comboMock, produtoAMock, produtoBMock, produtoCMock);

        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoBId, 1), item(produtoCId, 1)));

        // Nenhuma remoção
        verify(comboProdutoRepository, never()).deleteAll(any());

        // EXATAMENTE um saveAll com os 3 itens mantidos — nenhuma duplicação
        verify(comboProdutoRepository, times(1)).saveAll(any());
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 3
                    && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoBId, produtoCId))
                    && idsDosProdutos(lista).size() == lista.size();
        }));
    }

    // =========================================================================
    // CT-SYNC-03: ADICIONAR PRODUTO
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-03: Adicionar produto — A B → A B C (somente C é criado)")
    void testeAdicionarProduto() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 1)));
        stubProdutosEncontrados(comboMock, produtoAMock, produtoBMock, produtoCMock);

        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoBId, 1), item(produtoCId, 1)));

        // Nenhuma remoção
        verify(comboProdutoRepository, never()).deleteAll(any());

        // Exatamente 2 saveAll: um para criar C (1 item) e outro para manter A/B (2 itens)
        verify(comboProdutoRepository, times(2)).saveAll(any());
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoCId);
        }));
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoBId));
        }));
    }

    // =========================================================================
    // CT-SYNC-04: REMOVER E ADICIONAR
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-04: Remover e adicionar — A B C → A C D")
    void testeRemoverEAdicionar() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 1,
                        produtoCId, 1)));
        stubProdutosEncontrados(comboMock, produtoAMock, produtoCMock, produtoDMock);

        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1), item(produtoDId, 1)));

        // B removido
        verify(comboProdutoRepository, times(1)).deleteAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoBId);
        }));

        // D criado e A/C mantidos — exatamente 2 saveAll
        verify(comboProdutoRepository, times(2)).saveAll(any());
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoDId);
        }));
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoCId));
        }));
    }

    // =========================================================================
    // CT-SYNC-05: REMOVER VÁRIOS
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-05: Remover vários — A B C D → A D")
    void testeRemoverVarios() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 1,
                        produtoCId, 1,
                        produtoDId, 1)));
        stubProdutosEncontrados(comboMock, produtoAMock, produtoDMock);

        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoDId, 1)));

        // B e C removidos juntos
        verify(comboProdutoRepository, times(1)).deleteAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoBId, produtoCId));
        }));

        // A e D mantidos — exatamente 1 saveAll, sem recriações
        verify(comboProdutoRepository, times(1)).saveAll(any());
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoDId));
        }));
    }

    // =========================================================================
    // CT-SYNC-06: ALTERAR QUANTIDADE
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-06: Alterar quantidade — A:1 B:2 → A:3 B:2 (sem segundo vínculo para A)")
    void testeAlterarQuantidade() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 2)));
        stubProdutosEncontrados(comboMock, produtoAMock, produtoBMock);

        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 3), item(produtoBId, 2)));

        // Nenhuma remoção
        verify(comboProdutoRepository, never()).deleteAll(any());

        // Exatamente 1 saveAll: o mesmo vínculo de A atualizado para 3, B permanece 2
        verify(comboProdutoRepository, times(1)).saveAll(any());
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 &&
                    lista.stream().anyMatch(cp ->
                            cp.getProduto().getId().equals(produtoAId) && cp.getQuantidade() == 3) &&
                    lista.stream().anyMatch(cp ->
                            cp.getProduto().getId().equals(produtoBId) && cp.getQuantidade() == 2);
        }));

        // Garantia: NÃO foi criado um segundo vínculo para A (nem para B)
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.stream().filter(cp -> cp.getProduto().getId().equals(produtoAId)).count() == 1 &&
                    lista.stream().filter(cp -> cp.getProduto().getId().equals(produtoBId)).count() == 1;
        }));
    }

    // =========================================================================
    // CT-SYNC-07: EDIÇÃO REPETIDA
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-07: Edição repetida — A B C → A C → A C → A C D → A D")
    void testeEdicaoRepetida() {
        stubProdutosEncontrados(comboMock, produtoAMock, produtoCMock, produtoDMock);

        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoBId, 1, produtoCId, 1)),
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoCId, 1)),
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoCId, 1)),
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoCId, 1)),
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoCId, 1, produtoDId, 1))
                );

        InOrder inOrder = inOrder(comboProdutoRepository);

        // Etapa 1: A B C → A C
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).deleteAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoBId);
        }));
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoCId));
        }));

        // Etapa 2: A C → A C (sem alteração)
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoCId));
        }));

        // Etapa 3: A C → A C (repetição idêntica — nunca duplicar)
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoCId));
        }));

        // Etapa 4: A C → A C D
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1), item(produtoDId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoDId);
        }));
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoCId));
        }));

        // Etapa 5: A C D → A D
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoDId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).deleteAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoCId);
        }));
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoDId));
        }));

        verify(comboProdutoRepository, times(6)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return !lista.isEmpty() && idsDosProdutos(lista).size() == lista.size();
        }));
        verify(comboProdutoRepository, times(2)).deleteAll(any());
    }

    // =========================================================================
    // CT-SYNC-08: IDEMPOTÊNCIA
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-08: Idempotência — A C D executado duas vezes, sem novos vínculos")
    void testeIdempotencia() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoCId, 1,
                        produtoDId, 1)));
        stubProdutosEncontrados(comboMock, produtoAMock, produtoCMock, produtoDMock);

        List<ItemComposicaoRequestDTO> novosItens = List.of(
                item(produtoAId, 1),
                item(produtoCId, 1),
                item(produtoDId, 1));

        comboProdutoService.atualizarComposicaoDoCombo(comboId, novosItens);
        comboProdutoService.atualizarComposicaoDoCombo(comboId, novosItens);

        verify(comboProdutoRepository, never()).deleteAll(any());
        verify(comboProdutoRepository, times(2)).saveAll(any());
        verify(comboProdutoRepository, times(2)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 3
                    && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoCId, produtoDId))
                    && idsDosProdutos(lista).size() == lista.size();
        }));
    }

    // =========================================================================
    // CT-SYNC-09: PRODUTO INEXISTENTE
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-09: Produto inexistente — erro apropriado e nenhuma alteração parcial")
    void testeProdutoInexistente() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 1)));

        when(produtoRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(produtoRepository.findById(comboId)).thenReturn(Optional.of(comboMock));
        when(produtoRepository.findById(produtoAId)).thenReturn(Optional.of(produtoAMock));
        when(produtoRepository.findById(produtoBId)).thenReturn(Optional.of(produtoBMock));

        UUID produtoInexistente = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () ->
                comboProdutoService.atualizarComposicaoDoCombo(comboId,
                        List.of(item(produtoAId, 1), item(produtoBId, 1), item(produtoInexistente, 1))));

        // Nenhuma alteração parcial
        verify(comboProdutoRepository, times(1)).findByComboId(comboId);
        verify(comboProdutoRepository, never()).deleteAll(any());
        verify(comboProdutoRepository, never()).saveAll(any());
    }

    // =========================================================================
    // CT-SYNC-10: COMBO INEXISTENTE
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-10: Combo inexistente — erro apropriado e nenhuma alteração")
    void testeComboInexistente() {
        UUID comboInexistente = UUID.randomUUID();
        when(produtoRepository.findById(comboInexistente)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                comboProdutoService.atualizarComposicaoDoCombo(comboInexistente,
                        List.of(item(produtoAId, 1))));

        verify(comboProdutoRepository, never()).findByComboId(any());
        verify(comboProdutoRepository, never()).deleteAll(any());
        verify(comboProdutoRepository, never()).saveAll(any());
    }

    // =========================================================================
    // CT-SYNC-11: ROLLBACK
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-11: Rollback — falha durante a operação não persiste nada")
    void testeRollback() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 1,
                        produtoCId, 1)));
        when(produtoRepository.findById(comboId)).thenReturn(Optional.of(comboMock));
        when(produtoRepository.findById(produtoAId)).thenReturn(Optional.of(produtoAMock));
        when(produtoRepository.findById(produtoCId)).thenReturn(Optional.of(produtoCMock));
        when(produtoRepository.findById(produtoDId))
                .thenThrow(new RuntimeException("Falha simulada no banco de dados"));

        assertThrows(RuntimeException.class, () ->
                comboProdutoService.atualizarComposicaoDoCombo(comboId,
                        List.of(item(produtoAId, 1), item(produtoCId, 1), item(produtoDId, 1))));

        // O estado anterior permanece intacto — nenhuma escrita aconteceu
        verify(comboProdutoRepository, never()).deleteAll(any());
        verify(comboProdutoRepository, never()).saveAll(any());
    }

    // =========================================================================
    // CT-SYNC-12: PRESERVAÇÃO DOS VÍNCULOS EXISTENTES
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-12: Preservação dos vínculos — #1 A, #2 B, #3 C → A C D (A e C preservados)")
    void testePreservacaoDosVinculosExistentes() {
        UUID idVinculoA = UUID.randomUUID();
        UUID idVinculoB = UUID.randomUUID();
        UUID idVinculoC = UUID.randomUUID();

        ComboProduto vinculoA = criarVinculo(idVinculoA, produtoAMock, 1);
        ComboProduto vinculoB = criarVinculo(idVinculoB, produtoBMock, 1);
        ComboProduto vinculoC = criarVinculo(idVinculoC, produtoCMock, 1);

        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(List.of(vinculoA, vinculoB, vinculoC));
        stubProdutosEncontrados(comboMock, produtoAMock, produtoCMock, produtoDMock);

        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1), item(produtoDId, 1)));

        verify(comboProdutoRepository, times(1)).deleteAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getId().equals(idVinculoB);
        }));

        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoDId);
        }));

        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2 &&
                    lista.contains(vinculoA) && lista.contains(vinculoC) &&
                    lista.stream().anyMatch(cp -> cp == vinculoA) &&
                    lista.stream().anyMatch(cp -> cp == vinculoC);
        }));

        verify(comboProdutoRepository, times(2)).saveAll(any());
    }

    // =========================================================================
    // CT-SYNC-13: PRODUTO NÃO É APAGADO
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-13: Produto não é apagado — apenas o vínculo B é removido")
    void testeProdutoNaoEApagado() {
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 1,
                        produtoCId, 1)));
        stubProdutosEncontrados(comboMock, produtoAMock, produtoCMock);

        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1)));

        verify(comboProdutoRepository, times(1)).deleteAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoBId);
        }));

        verify(produtoRepository, never()).delete(any());
        verify(produtoRepository, never()).deleteById(any());

        assertNotNull(produtoBMock);
        assertEquals(produtoBId, produtoBMock.getId());
    }

    // =========================================================================
    // CT-SYNC-14: ISOLAMENTO ENTRE COMBOS
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-14: Isolamento entre combos — editar Combo 1 não afeta Combo 2")
    void testeIsolamentoEntreCombos() {
        UUID combo2Id = UUID.randomUUID();

        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(criarAssociacoesIniciais(Map.of(
                        produtoAId, 1,
                        produtoBId, 1)));
        stubProdutosEncontrados(comboMock, produtoAMock);

        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1)));

        verify(comboProdutoRepository, times(1)).deleteAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoBId);
        }));
        verify(comboProdutoRepository, times(1)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoAId);
        }));

        verify(comboProdutoRepository, never()).findByComboId(combo2Id);
        verify(comboProdutoRepository, never()).deleteAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.isEmpty() || !lista.get(0).getCombo().getId().equals(lista.get(lista.size() - 1).getCombo().getId());
        }));
        verify(comboProdutoRepository, never()).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.isEmpty() || !lista.get(0).getCombo().getId().equals(comboId);
        }));
    }

    // =========================================================================
    // CT-SYNC-15: PRODUTO DUPLICADO NO REQUEST
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-15: Produto duplicado no request — PUT rejeitado antes de qualquer alteração")
    void testeProdutoDuplicadoNoRequest() {
        when(produtoRepository.findById(comboId)).thenReturn(Optional.of(comboMock));

        assertThrows(BusinessRuleException.class, () ->
                comboProdutoService.atualizarComposicaoDoCombo(comboId,
                        List.of(item(produtoAId, 1), item(produtoAId, 2), item(produtoCId, 1))));

        // Estado anterior permanece intacto: a validação ocorre antes de consultar/alterar a estrutura
        verify(comboProdutoRepository, never()).findByComboId(any());
        verify(comboProdutoRepository, never()).deleteAll(any());
        verify(comboProdutoRepository, never()).saveAll(any());
    }

    // =========================================================================
    // CT-SYNC-16: POST DUPLICADO
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-16: POST duplicado — segundo POST para o mesmo combo+produto é bloqueado (sem duplicação)")
    void testePostDuplicado() {
        ComboProdutoRequestDTO dto = new ComboProdutoRequestDTO(comboId, produtoAId, 1);
        ComboProduto vinculoExistente = criarVinculo(UUID.randomUUID(), produtoAMock, 1);

        when(produtoRepository.findById(comboId)).thenReturn(Optional.of(comboMock));
        when(produtoRepository.findById(produtoAId)).thenReturn(Optional.of(produtoAMock));
        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(List.of())
                .thenReturn(List.of(vinculoExistente));
        when(comboProdutoRepository.save(any(ComboProduto.class))).thenReturn(vinculoExistente);

        assertNotNull(comboProdutoService.associarProdutoAoCombo(dto));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> comboProdutoService.associarProdutoAoCombo(dto));
        assertTrue(ex.getMessage().contains("já está associado"));

        verify(comboProdutoRepository, times(1)).save(any(ComboProduto.class));
    }

    // =========================================================================
    // CT-SYNC-17: REQUEST INVÁLIDO NÃO ALTERA COMPOSIÇÃO
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-17: Request inválido não altera composição — A B C → A A C permanece A B C")
    void testeRequestInvalidoNaoAlteraComposicao() {
        when(produtoRepository.findById(comboId)).thenReturn(Optional.of(comboMock));

        assertThrows(BusinessRuleException.class, () ->
                comboProdutoService.atualizarComposicaoDoCombo(comboId,
                        List.of(item(produtoAId, 1), item(produtoAId, 1), item(produtoCId, 1))));

        verify(comboProdutoRepository, never()).findByComboId(any());
        verify(comboProdutoRepository, never()).deleteAll(any());
        verify(comboProdutoRepository, never()).saveAll(any());
    }

    // =========================================================================
    // CT-SYNC-18: EDIÇÃO COMPLETA
    // =========================================================================

    @Test
    @DisplayName("CT-SYNC-18: Edição completa — A B C → A C → A C D → A D → A B D → A B")
    void testeEdicaoCompleta() {
        stubProdutosEncontrados(comboMock, produtoAMock, produtoBMock, produtoCMock, produtoDMock);

        when(comboProdutoRepository.findByComboId(comboId))
                .thenReturn(
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoBId, 1, produtoCId, 1)),
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoCId, 1)),
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoCId, 1, produtoDId, 1)),
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoDId, 1)),
                        criarAssociacoesIniciais(Map.of(produtoAId, 1, produtoBId, 1, produtoDId, 1))
                );

        InOrder inOrder = inOrder(comboProdutoRepository);

        // Etapa 1: A B C → A C
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).deleteAll(any());
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2
                    && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoCId))
                    && idsDosProdutos(lista).size() == lista.size();
        }));

        // Etapa 2: A C → A C D
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoCId, 1), item(produtoDId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoDId);
        }));
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2
                    && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoCId))
                    && idsDosProdutos(lista).size() == lista.size();
        }));

        // Etapa 3: A C D → A D
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoDId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).deleteAll(any());
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2
                    && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoDId))
                    && idsDosProdutos(lista).size() == lista.size();
        }));

        // Etapa 4: A D → A B D
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoBId, 1), item(produtoDId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 1 && lista.get(0).getProduto().getId().equals(produtoBId);
        }));
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2
                    && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoDId))
                    && idsDosProdutos(lista).size() == lista.size();
        }));

        // Etapa 5: A B D → A B
        comboProdutoService.atualizarComposicaoDoCombo(comboId,
                List.of(item(produtoAId, 1), item(produtoBId, 1)));
        inOrder.verify(comboProdutoRepository).findByComboId(comboId);
        inOrder.verify(comboProdutoRepository).deleteAll(any());
        inOrder.verify(comboProdutoRepository).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return lista.size() == 2
                    && idsDosProdutos(lista).equals(Set.of(produtoAId, produtoBId))
                    && idsDosProdutos(lista).size() == lista.size();
        }));

        // Blindagem global: 7 saveAll no total, TODOS sem produtos duplicados na mesma lista
        verify(comboProdutoRepository, times(7)).saveAll(argThat(iteravel -> {
            List<ComboProduto> lista = converterParaLista(iteravel);
            return !lista.isEmpty() && idsDosProdutos(lista).size() == lista.size();
        }));

        // Blindagem global: exatamente 3 deleteAll (B, C, D ao longo das etapas)
        verify(comboProdutoRepository, times(3)).deleteAll(any());
    }
}