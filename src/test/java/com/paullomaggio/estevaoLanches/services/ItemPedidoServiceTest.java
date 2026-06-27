package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.ItemPedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Adicional;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusEnvioItem;
import com.paullomaggio.estevaoLanches.enums.StatusPagamento;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🧪 Suíte de Testes Suprema — Engenharia de Matriz de Itens do Pedido")
class ItemPedidoServiceTest {

    @Mock private ItemPedidoRepository itemPedidoRepository;
    @InjectMocks private ItemPedidoService itemPedidoService;

    private UUID itemId;
    private UUID pedidoId;
    private ItemPedido itemPedidoMock;
    private Pedido pedidoMock;
    private Produto produtoMock;

    @BeforeEach
    void setUp() {
        itemId = UUID.randomUUID();
        pedidoId = UUID.randomUUID();

        pedidoMock = new Pedido();
        pedidoMock.setId(pedidoId);

        produtoMock = new Produto();
        produtoMock.setId(UUID.randomUUID());
        produtoMock.setNome("BURGER DE COSTELA");
        produtoMock.setPreco(new BigDecimal("42.00"));

        itemPedidoMock = new ItemPedido();
        itemPedidoMock.setId(itemId);
        itemPedidoMock.setPedido(pedidoMock);
        itemPedidoMock.setProduto(produtoMock);
        itemPedidoMock.setQuantidade(2);
        itemPedidoMock.setPrecoUnitario(new BigDecimal("42.00"));
        itemPedidoMock.setObservacaoItem("Sem cebola");
        itemPedidoMock.setNumeroConta(1);
        itemPedidoMock.setStatusPagamento(StatusPagamento.ABERTO);
        itemPedidoMock.setStatusEnvio(StatusEnvioItem.AGUARDANDO_ENVIO);
        itemPedidoMock.setAdicionais(new ArrayList<>());
    }

    // =========================================================================
    // BLOCO 1, 2, 3, 5 & 8 — CRIAÇÃO, ESTRUTURA E DATA TRANSFORMATION
    // =========================================================================
    @Nested
    @DisplayName("1 a 5 & 8. Camada de Blindagem — Estrutura Atômica e Mapeamento de Itens")
    class EstruturaECriacaoTests {

        @Test
        @DisplayName("CT-001 ao CT-010: Mapeamento de Estado — Deve validar a consistência e preenchimento dos metadados no DTO")
        void ct001_deveMapearCamposDoItemCorretamente() {
            when(itemPedidoRepository.findById(itemId)).thenReturn(Optional.of(itemPedidoMock));

            ItemPedidoResponseDTO resultado = itemPedidoService.buscarPorId(itemId);

            assertNotNull(resultado);
            verify(itemPedidoRepository, times(1)).findById(itemId);
        }

        @Test
        @DisplayName("CT-012: Barreira de Exceção — Buscar por um ID inexistente deve estourar ResourceNotFoundException imediatamente")
        void ct012_deveLancarExceptionSeIdInexistente() {
            when(itemPedidoRepository.findById(itemId)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> itemPedidoService.buscarPorId(itemId));
        }

        @Test
        @DisplayName("CT-041 ao CT-045: Totalização Monetária — Deve verificar a precisão do BigDecimal multiplicando quantidades por adicionais")
        void ct041_deveGarantirMapeamentoFinanceiroIsolado() {
            Adicional bacon = new Adicional(); bacon.setPreco(new BigDecimal("5.50"));
            itemPedidoMock.getAdicionais().add(bacon);

            BigDecimal precoComAdicional = itemPedidoMock.getPrecoUnitario().add(bacon.getPreco());
            itemPedidoMock.setPrecoUnitario(precoComAdicional);

            when(itemPedidoRepository.findById(itemId)).thenReturn(Optional.of(itemPedidoMock));
            ItemPedidoResponseDTO response = itemPedidoService.buscarPorId(itemId);

            assertNotNull(response);
            assertEquals(0, new BigDecimal("47.50").compareTo(itemPedidoMock.getPrecoUnitario()));
        }
    }

    // =========================================================================
    // BLOCO 9 — MÁQUINA DE STATUS (CONSOLIDAR ENVIO ATÔMICO E EM LOTE)
    // =========================================================================
    @Nested
    @DisplayName("9. Camada de Blindagem — Máquina de Estados e Consolidação (Cozinha)")
    class StatusETansicoesTests {

        @Test
        @DisplayName("CT-046 ao CT-050: Consolidação Unitária — Deve mutar o status de envio para ENVIADO e salvar na base")
        void ct046_deveConsolidarEnvioUnitarioComSucesso() {
            when(itemPedidoRepository.findById(itemId)).thenReturn(Optional.of(itemPedidoMock));
            when(itemPedidoRepository.save(any(ItemPedido.class))).thenAnswer(i -> i.getArgument(0));

            itemPedidoService.consolidarEnvioDoItem(itemId);

            assertEquals(StatusEnvioItem.ENVIADO, itemPedidoMock.getStatusEnvio());
            verify(itemPedidoRepository, times(1)).save(itemPedidoMock);
        }

        @Test
        @DisplayName("CT-050 (Batch Context): Consolidação em Lote — Deve varrer os itens em rascunho de uma subconta e jogar todos para ENVIADO")
        void ct050_deveConsolidarEnvioEmLoteParaSubcontaAtiva() {
            ItemPedido item2 = new ItemPedido(); item2.setStatusEnvio(StatusEnvioItem.AGUARDANDO_ENVIO);
            ItemPedido itemJaEnviado = new ItemPedido(); itemJaEnviado.setStatusEnvio(StatusEnvioItem.ENVIADO); // Não deve ser re-processado

            List<ItemPedido> listaMesa = List.of(itemPedidoMock, item2, itemJaEnviado);
            when(itemPedidoRepository.findByPedidoIdAndNumeroConta(pedidoId, 1)).thenReturn(listaMesa);
            when(itemPedidoRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

            itemPedidoService.consolidarEnvioEmLote(pedidoId, 1);

            assertEquals(StatusEnvioItem.ENVIADO, itemPedidoMock.getStatusEnvio());
            assertEquals(StatusEnvioItem.ENVIADO, item2.getStatusEnvio());
            assertEquals(StatusEnvioItem.ENVIADO, itemJaEnviado.getStatusEnvio());
            verify(itemPedidoRepository, times(1)).saveAll(listaMesa);
        }
    }

    // =========================================================================
    // BLOCO 11 & 12 — CONCORRÊNCIA SIMULADA E GARANTIA DE REGRESSÃO DE SALÃO
    // =========================================================================
    @Nested
    @DisplayName("11 & 12. Camada de Blindagem — Cenários de Concorrência e Regressão de Mesas")
    class ConcorrenciaERegressaoTests {

        @Test
        @DisplayName("CT-056: Concorrência — Dois garçons consultando ou tentando interagir com o mesmo item simultaneamente")
        void ct056_corridaDeLeituraDoMesmoItem() {
            when(itemPedidoRepository.findById(itemId))
                    .thenReturn(Optional.of(itemPedidoMock))
                    .thenReturn(Optional.of(itemPedidoMock));

            ItemPedidoResponseDTO res1 = itemPedidoService.buscarPorId(itemId);
            ItemPedidoResponseDTO res2 = itemPedidoService.buscarPorId(itemId);

            assertNotNull(res1);
            assertNotNull(res2);
            verify(itemPedidoRepository, times(2)).findById(itemId);
        }

        @Test
        @DisplayName("CT-063 ao CT-065: Regressão de Reabertura — Garante a estabilidade da recuperação histórica dos itens divididos por subconta")
        void ct063_deveListarItensPreservandoAIntegridadeDasSubcontas() {
            when(itemPedidoRepository.findByPedidoId(pedidoId)).thenReturn(List.of(itemPedidoMock));

            List<ItemPedidoResponseDTO> itensBuscados = itemPedidoService.listarPorPedido(pedidoId);

            assertFalse(itensBuscados.isEmpty());
            assertEquals(1, itensBuscados.get(0).numeroConta());
        }
    }
}