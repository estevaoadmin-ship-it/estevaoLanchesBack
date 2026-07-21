package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ItemComboRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemComboResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.ItemCombo;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.AdicionalRepository;
import com.paullomaggio.estevaoLanches.repositories.ItemComboRepository;
import com.paullomaggio.estevaoLanches.repositories.ItemPedidoRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    @Mock private AdicionalRepository adicionalRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private PedidoService pedidoService;
    @Mock private AdicionalValidationService adicionalValidationService; // NOVO MOCK

    private ItemComboService itemComboService;

    private UUID itemPedidoId;
    private UUID produtoFilhoId;
    private UUID itemComboId;
    private UUID pedidoId;

    private ItemPedido itemPedidoMock;
    private ItemCombo itemComboMock;
    private Produto produtoInternoMock;
    private Pedido pedidoMock;

    @BeforeEach
    void setUp() {
        // Instanciação manual do serviço com os mocks atualizados
        itemComboService = new ItemComboService(
                itemComboRepository,
                itemPedidoRepository,
                adicionalRepository,
                produtoRepository,
                pedidoService,
                adicionalValidationService // Passando novo mock
        );

        itemPedidoId = UUID.randomUUID();
        produtoFilhoId = UUID.randomUUID();
        itemComboId = UUID.randomUUID();
        pedidoId = UUID.randomUUID();

        pedidoMock = new Pedido();
        pedidoMock.setId(pedidoId);

        itemPedidoMock = new ItemPedido();
        itemPedidoMock.setId(itemPedidoId);
        itemPedidoMock.setPedido(pedidoMock); // Vincular ItemPedido ao Pedido

        produtoInternoMock = new Produto();
        produtoInternoMock.setId(produtoFilhoId);
        produtoInternoMock.setNome("X-Bacon");
        produtoInternoMock.setAdicionais(new ArrayList<>()); // Inicializar lista de adicionais permitidos

        itemComboMock = new ItemCombo();
        itemComboMock.setId(itemComboId);
        itemComboMock.setItemPedido(itemPedidoMock);
        itemComboMock.setProdutoId(produtoFilhoId);
        itemComboMock.setNomeProduto("BATATA FRITA M");
        itemComboMock.setQuantidade(1);
        itemComboMock.setPrecoUnitario(BigDecimal.ZERO);
        itemComboMock.setAdicionais(new ArrayList<>()); // Inicializar lista de adicionais do itemCombo
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

    // =========================================================================
    // NOVOS TESTES PARA ATUALIZAR ADICIONAIS DO ITEM COMBO
    // =========================================================================
    @Nested
    @DisplayName("Novos Testes — Atualização de Adicionais em ItemCombo")
    class AtualizarAdicionaisItemComboTests {

        private Adicional adicional1;
        private Adicional adicional2;
        private PedidoResponseDTO pedidoResponseDTOMock;

        @BeforeEach
        void setupAdicionais() {
            adicional1 = new Adicional(UUID.randomUUID(), "Bacon Extra", BigDecimal.valueOf(3.00));
            adicional2 = new Adicional(UUID.randomUUID(), "Cheddar", BigDecimal.valueOf(2.50));

            produtoInternoMock.getAdicionais().add(adicional1); // Adicional permitido para o produto
            produtoInternoMock.getAdicionais().add(adicional2); // Adicional permitido para o produto

            pedidoMock.setItens(List.of(itemPedidoMock)); // Adicionar itemPedidoMock ao pedidoMock
            itemPedidoMock.setProduto(new Produto()); // Produto do itemPedidoMock
            itemPedidoMock.getProduto().setIsCombo(true); // Marcar como combo

            pedidoResponseDTOMock = new PedidoResponseDTO(pedidoMock);
        }

        @Test
        @DisplayName("CT-NOVO-001: Deve adicionar adicionais a um ItemCombo com sucesso")
        void deveAdicionarAdicionaisComSucesso() {
            when(itemComboRepository.findById(itemComboId))
                    .thenReturn(Optional.of(itemComboMock));

            when(adicionalValidationService.validarAdicionaisPermitidos(
                    eq(produtoFilhoId),
                    anyList()
            )).thenReturn(List.of(adicional1));

            when(itemComboRepository.save(any(ItemCombo.class)))
                    .thenReturn(itemComboMock);

            when(pedidoService.recalcularTotalPedido(pedidoId))
                    .thenReturn(pedidoResponseDTOMock);

            PedidoResponseDTO resultado =
                    itemComboService.atualizarAdicionaisDoItemCombo(
                            itemComboId,
                            List.of(adicional1.getId())
                    );

            assertNotNull(resultado);

            verify(itemComboRepository, times(1))
                    .findById(itemComboId);

            verify(produtoRepository, never())
                    .findById(produtoFilhoId);

            verify(adicionalValidationService, times(1))
                    .validarAdicionaisPermitidos(eq(produtoFilhoId), anyList());

            verify(itemComboRepository, times(1))
                    .save(argThat(ic -> ic.getAdicionais().contains(adicional1)));

            verify(pedidoService, times(1))
                    .recalcularTotalPedido(pedidoId);
        }

        @Test
        @DisplayName("CT-NOVO-002: Deve remover todos os adicionais de um ItemCombo com sucesso")
        void deveRemoverTodosAdicionaisComSucesso() {
            itemComboMock.getAdicionais().add(adicional1); // Adicionar um adicional para remover

            when(itemComboRepository.findById(itemComboId)).thenReturn(Optional.of(itemComboMock));
            when(adicionalValidationService.validarAdicionaisPermitidos(eq(produtoFilhoId), anyList())).thenReturn(Collections.emptyList());
            when(itemComboRepository.save(any(ItemCombo.class))).thenReturn(itemComboMock);
            when(pedidoService.recalcularTotalPedido(pedidoId)).thenReturn(pedidoResponseDTOMock);

            PedidoResponseDTO resultado = itemComboService.atualizarAdicionaisDoItemCombo(itemComboId, Collections.emptyList());

            assertNotNull(resultado);
            verify(itemComboRepository, times(1)).save(argThat(ic -> ic.getAdicionais().isEmpty()));
            verify(pedidoService, times(1)).recalcularTotalPedido(pedidoId);
        }

        @Test
        @DisplayName("CT-NOVO-003: Deve substituir a seleção de adicionais de um ItemCombo com sucesso")
        void deveSubstituirAdicionaisComSucesso() {
            itemComboMock.getAdicionais().add(adicional1); // Adicional inicial

            when(itemComboRepository.findById(itemComboId)).thenReturn(Optional.of(itemComboMock));
            when(adicionalValidationService.validarAdicionaisPermitidos(eq(produtoFilhoId), anyList())).thenReturn(List.of(adicional2));
            when(itemComboRepository.save(any(ItemCombo.class))).thenReturn(itemComboMock);
            when(pedidoService.recalcularTotalPedido(pedidoId)).thenReturn(pedidoResponseDTOMock);

            PedidoResponseDTO resultado = itemComboService.atualizarAdicionaisDoItemCombo(itemComboId, List.of(adicional2.getId()));

            assertNotNull(resultado);
            verify(itemComboRepository, times(1)).save(argThat(ic ->
                    ic.getAdicionais().size() == 1 && ic.getAdicionais().contains(adicional2)
            ));
            verify(pedidoService, times(1)).recalcularTotalPedido(pedidoId);
        }

        @Test
        @DisplayName("CT-NOVO-004: Deve rejeitar adicionais se ItemCombo não for encontrado")
        void deveRejeitarSeItemComboNaoEncontrado() {
            when(itemComboRepository.findById(itemComboId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    itemComboService.atualizarAdicionaisDoItemCombo(itemComboId, List.of(adicional1.getId())));
            verify(produtoRepository, never()).findById(any());
            verify(adicionalValidationService, never()).validarAdicionaisPermitidos(any(), anyList());
            verify(adicionalRepository, never()).findAllById(any());
            verify(itemComboRepository, never()).save(any());
            verify(pedidoService, never()).recalcularTotalPedido(any());
        }

        @Test
        @DisplayName("CT-NOVO-005: Deve rejeitar adicionais se Produto interno não for encontrado")
        void deveRejeitarSeProdutoInternoNaoEncontrado() {
            // Este cenário não deve mais ocorrer, pois a validação é feita pelo AdicionalValidationService
            // que já lida com a busca do produto interno.
            // O mock de produtoRepository.findById(produtoFilhoId) não é mais necessário aqui.
            when(itemComboRepository.findById(itemComboId)).thenReturn(Optional.of(itemComboMock));
            when(adicionalValidationService.validarAdicionaisPermitidos(eq(produtoFilhoId), anyList()))
                    .thenThrow(new ResourceNotFoundException("Produto interno não localizado para validação de adicionais."));

            assertThrows(ResourceNotFoundException.class, () ->
                    itemComboService.atualizarAdicionaisDoItemCombo(itemComboId, List.of(adicional1.getId())));
            verify(itemComboRepository, never()).save(any());
            verify(pedidoService, never()).recalcularTotalPedido(any());
        }

        @Test
        @DisplayName("CT-NOVO-006: Deve rejeitar adicional inexistente (ID inválido)")
        void deveRejeitarAdicionalInexistente() {
            UUID adicionalInexistenteId = UUID.randomUUID();

            when(itemComboRepository.findById(itemComboId)).thenReturn(Optional.of(itemComboMock));
            when(adicionalValidationService.validarAdicionaisPermitidos(eq(produtoFilhoId), anyList()))
                    .thenThrow(new BusinessRuleException("Adicionais não encontrados: [" + adicionalInexistenteId + "]"));

            assertThrows(BusinessRuleException.class, () ->
                    itemComboService.atualizarAdicionaisDoItemCombo(itemComboId, List.of(adicional1.getId(), adicionalInexistenteId)));
            verify(itemComboRepository, never()).save(any());
            verify(pedidoService, never()).recalcularTotalPedido(any());
        }

        @Test
        @DisplayName("CT-NOVO-007: Deve rejeitar adicional não permitido para o produto interno")
        void deveRejeitarAdicionalNaoPermitido() {
            Adicional adicionalNaoPermitido = new Adicional(UUID.randomUUID(), "Picles", BigDecimal.valueOf(1.00));

            when(itemComboRepository.findById(itemComboId)).thenReturn(Optional.of(itemComboMock));
            when(adicionalValidationService.validarAdicionaisPermitidos(eq(produtoFilhoId), anyList()))
                    .thenThrow(new BusinessRuleException("Adicional 'Picles' não permitido para o produto 'X-Bacon'."));

            assertThrows(BusinessRuleException.class, () ->
                    itemComboService.atualizarAdicionaisDoItemCombo(itemComboId, List.of(adicionalNaoPermitido.getId())));
            verify(itemComboRepository, never()).save(any());
            verify(pedidoService, never()).recalcularTotalPedido(any());
        }
    }
}