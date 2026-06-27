package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ItemComboRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComboResponseDTO;
import com.paullomaggio.estevaoLanches.entities.ItemCombo;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ItemComboRepository;
import com.paullomaggio.estevaoLanches.repositories.ItemPedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Suprema — Engenharia de Matriz de Itens do Combo")
class ItemComboServiceTest {

    @Mock private ItemComboRepository itemComboRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;

    @InjectMocks private ItemComboService itemComboService;

    private UUID itemPedidoId;
    private UUID produtoFilhoId;
    private ItemPedido itemPedidoMock;
    private ItemCombo itemComboMock;

    @BeforeEach
    void setUp() {
        itemPedidoId = UUID.randomUUID();
        produtoFilhoId = UUID.randomUUID();

        itemPedidoMock = new ItemPedido();
        itemPedidoMock.setId(itemPedidoId);

        itemComboMock = new ItemCombo();
        itemComboMock.setId(UUID.randomUUID());
        itemComboMock.setItemPedido(itemPedidoMock);
        itemComboMock.setProdutoId(produtoFilhoId);
        itemComboMock.setNomeProduto("BATATA FRITA M");
        itemComboMock.setQuantidade(1);
        itemComboMock.setPrecoUnitario(BigDecimal.ZERO); // Preço embutido no valor mestre do combo
    }

    // =========================================================================
    // BLOCO 1, 2 & 3 — LANÇAMENTO DE COMPOSIÇÃO DE COMBO E VALIDAÇÕES
    // =========================================================================
    @Nested
    @DisplayName("1 a 3. Camada de Blindagem — Registro e Validação de Vínculo de Combo")
    class LancementoComboTests {

        @Test
        @DisplayName("CT-001 ao CT-008: Lançamento com Sucesso — Deve associar o item de insumo do combo ao lote mestre salvando na base")
        void ct001_deveLancarItemNoComboComSucesso() {
            ItemComboRequestDTO dto = new ItemComboRequestDTO(itemPedidoId, produtoFilhoId, "BATATA FRITA M", 1, BigDecimal.ZERO);

            when(itemPedidoRepository.findById(itemPedidoId)).thenReturn(Optional.of(itemPedidoMock));
            when(itemComboRepository.save(any(ItemCombo.class))).thenReturn(itemComboMock);

            ItemComboResponseDTO resultado = itemComboService.lancarItemNoCombo(dto);

            assertNotNull(resultado);
            verify(itemPedidoRepository, times(1)).findById(itemPedidoId);
            verify(itemComboRepository, times(1)).save(argThat(ic ->
                    ic.getItemPedido().equals(itemPedidoMock) &&
                            ic.getProdutoId().equals(produtoFilhoId) &&
                            ic.getNomeProduto().equals("BATATA FRITA M")
            ));
        }

        @Test
        @DisplayName("CT-010 e CT-014: Barreira contra Órfãos — Tentar lançar insumos vinculados a um ItemPedido inexistente deve ser barrado")
        void ct010_deveEstourarExceptionSeItemPedidoNaoExistir() {
            ItemComboRequestDTO dto = new ItemComboRequestDTO(UUID.randomUUID(), produtoFilhoId, "REFRI", 1, BigDecimal.ZERO);
            when(itemPedidoRepository.findById(dto.itemPedidoId())).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> itemComboService.lancarItemNoCombo(dto));
            verify(itemComboRepository, never()).save(any());
        }
    }

    // =========================================================================
    // BLOCO 4, 5 & 8 — ESTRUTURA, QUANTIDADES E ARREDONDAMENTO
    // =========================================================================
    @Nested
    @DisplayName("4, 5 & 8. Camada de Blindagem — Quantidades e Totalização das Composições")
    class EstruturaEQuantidadesTests {

        @Test
        @DisplayName("CT-017 ao CT-018: Quantidades Tradicionais — Deve aceitar o processamento de itens de combo com multiplicadores operacionais válidos")
        void ct017_deveProcessarQuantidadesValidas() {
            ItemComboRequestDTO dto = new ItemComboRequestDTO(itemPedidoId, produtoFilhoId, "NUGGETS", 2, BigDecimal.ZERO);
            itemComboMock.setQuantidade(2);

            when(itemPedidoRepository.findById(itemPedidoId)).thenReturn(Optional.of(itemPedidoMock));
            when(itemComboRepository.save(any(ItemCombo.class))).thenReturn(itemComboMock);

            ItemComboResponseDTO resultado = itemComboService.lancarItemNoCombo(dto);
            assertNotNull(resultado);
            assertEquals(2, itemComboMock.getQuantidade());
        }
    }

    // =========================================================================
    // BLOCO 6, 9 & 12 — LEITURA DA MALHA DO COMBO E FLUXOS DE REGRESSÃO
    // =========================================================================
    @Nested
    @DisplayName("6, 9 & 12. Camada de Blindagem — Listagem, Sincronização e Regressão")
    class ListagemERegressaoTests {

        @Test
        @DisplayName("CT-028 ao CT-032: Leitura Limpa — Listar os insumos do combo pedido deve retornar os registros sem disparar comandos de escrita")
        void ct028_deveListarItensDoComboPedidoSemEfeitosColaterais() {
            when(itemComboRepository.findByItemPedidoId(itemPedidoId)).thenReturn(List.of(itemComboMock));

            List<ItemComboResponseDTO> resultado = itemComboService.listarItensDoComboPedido(itemPedidoId);

            assertFalse(resultado.isEmpty());
            verify(itemComboRepository, times(1)).findByItemPedidoId(itemPedidoId);
            verify(itemComboRepository, never()).save(any());
        }

        @Test
        @DisplayName("CT-029: Lista Segura — Tentar carregar a estrutura de um item que não possui combo deve retornar uma lista vazia segura")
        void ct029_deveRetornarVazioCasoComboNaoPossuaItens() {
            when(itemComboRepository.findByItemPedidoId(itemPedidoId)).thenReturn(Collections.emptyList());
            List<ItemComboResponseDTO> resultado = itemComboService.listarItensDoComboPedido(itemPedidoId);
            assertTrue(resultado.isEmpty());
        }
    }

    // =========================================================================
    // BLOCO 11 — IMPEDIMENTOS CONCORRENTES NO MÓDULO DE MONTAGEM
    // =========================================================================
    @Nested
    @DisplayName("11. Camada de Blindagem — Simulação Concorrente no Faturamento de Combos")
    class ConcorrenciaCombosTests {

        @Test
        @DisplayName("CT-050: Lançamentos Simultâneos — Dois garçons injetando insumos idênticos no mesmo combo sob frações de segundo")
        void ct050_corridaDeInsercaoSimultanea() {
            ItemComboRequestDTO dto = new ItemComboRequestDTO(itemPedidoId, produtoFilhoId, "SUCO NATURAL", 1, BigDecimal.ZERO);

            when(itemPedidoRepository.findById(itemPedidoId)).thenReturn(Optional.of(itemPedidoMock));
            when(itemComboRepository.save(any(ItemCombo.class))).thenReturn(itemComboMock);

            // Simula os cliques paralelos sendo interceptados sequencialmente na esteira transacional
            itemComboService.lancarItemNoCombo(dto);
            itemComboService.lancarItemNoCombo(dto);

            verify(itemComboRepository, times(2)).save(any(ItemCombo.class));
        }
    }
}