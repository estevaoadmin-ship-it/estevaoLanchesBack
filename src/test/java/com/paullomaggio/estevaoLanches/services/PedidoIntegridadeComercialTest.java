package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("🛡️ Suíte de Blindagem Comercial — Integridade Financeira")
public class PedidoIntegridadeComercialTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private CaixaRepository caixaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private FilaImpressaoRepository filaImpressaoRepository;
    @Mock private AdicionalRepository adicionalRepository;
    @Mock private ComandaRepository comandaRepository;
    @Mock private ContaRepository contaRepository;
    @Mock private CarrinhoRepository carrinhoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private PedidoService pedidoService;

    private UUID prodIdLanche;
    private UUID comandaId;
    private UUID contaId;
    private Produto lancheQuente;
    private Comanda comandaMestre;
    private Conta contaMestre;

    @BeforeEach
    void setUp() {
        prodIdLanche = UUID.randomUUID();
        comandaId = UUID.randomUUID();
        contaId = UUID.randomUUID();

        Mesa mesa = new Mesa();
        mesa.setId(UUID.randomUUID());
        mesa.setNumero(10);

        comandaMestre = new Comanda();
        comandaMestre.setId(comandaId);
        comandaMestre.setMesa(mesa);
        comandaMestre.setStatus(StatusComanda.ABERTA);

        contaMestre = new Conta();
        contaMestre.setId(contaId);
        contaMestre.setNumeroConta(1);
        contaMestre.setComanda(comandaMestre);
        contaMestre.setValorTotal(BigDecimal.ZERO);
        contaMestre.setPedidos(new ArrayList<>());

        lancheQuente = new Produto();
        lancheQuente.setId(prodIdLanche);
        lancheQuente.setNome("X-TUDO MONSTRO");
        lancheQuente.setPreco(new BigDecimal("25.00"));
        lancheQuente.setPrecisaPreparo(true);
        lancheQuente.setAdicionais(new ArrayList<>());

        Categoria categoria = new Categoria();
        categoria.setId(UUID.randomUUID());
        lancheQuente.setCategoria(categoria);

        // 🎯 FIX STRICTURE: Mocks preventivos reconfigurados como lenient contra travas de stubbing não utilizados
        lenient().when(contaRepository.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(filaImpressaoRepository.save(any(FilaImpressao.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(pedidoRepository.saveAndFlush(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Nested
    @DisplayName("Bloco 1 — Integridade da Quantidade")
    class IntegridadeQuantidadeTests {

        @Test
        @DisplayName("CT001: Validação estrita de payloads e acurácia de quantidades")
        void deveGarantirAcuraciaMatematicaDeQuantidades() {
            PedidoMobileRequestDTO.ItemMobileRequestDTO itemDto = new PedidoMobileRequestDTO.ItemMobileRequestDTO(prodIdLanche, 2, "Sem cebola", new ArrayList<>());
            PedidoMobileRequestDTO payload = new PedidoMobileRequestDTO(comandaId, 10, 1, null, List.of(itemDto));

            // 🎯 FIX STRICTURE LOCAL: Isolado com lenient() para blindar a execução do Surefire
            lenient().when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
            lenient().when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaMestre));
            lenient().when(contaRepository.findByComandaIdAndNumeroConta(comandaId, 1)).thenReturn(Optional.of(contaMestre));
            lenient().when(produtoRepository.findById(prodIdLanche)).thenReturn(Optional.of(lancheQuente));
            lenient().when(pedidoRepository.findByContaIdIn(any())).thenReturn(new ArrayList<>());

            PedidoResponseDTO res = pedidoService.processarPedidoMobile(payload);

            assertThat(res.itens()).hasSize(1);
            assertThat(res.itens().getFirst().quantidade()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Bloco 2 — Regressão Estrutural")
    class RegressaoEreconciliacaoTests {

        @Test
        @DisplayName("NenhumBugPodeAlterarValorFinanceiro — Teste de Reconciliação das Fontes")
        void NenhumBugPodeAlterarValorFinanceiro() {
            int quantity = 2;
            BigDecimal precoProduto = new BigDecimal("25.00");
            BigDecimal valorFinanceiroEsperado = precoProduto.multiply(BigDecimal.valueOf(quantity));

            PedidoMobileRequestDTO.ItemMobileRequestDTO itemDto = new PedidoMobileRequestDTO.ItemMobileRequestDTO(prodIdLanche, quantity, "Bem passado", new ArrayList<>());
            PedidoMobileRequestDTO payload = new PedidoMobileRequestDTO(comandaId, 15, 1, null, List.of(itemDto));

            lenient().when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
            lenient().when(comandaRepository.findById(comandaId)).thenReturn(Optional.of(comandaMestre));
            lenient().when(contaRepository.findByComandaIdAndNumeroConta(comandaId, 1)).thenReturn(Optional.of(contaMestre));
            lenient().when(produtoRepository.findById(prodIdLanche)).thenReturn(Optional.of(lancheQuente));
            lenient().when(pedidoRepository.findByContaIdIn(any())).thenReturn(new ArrayList<>());

            PedidoResponseDTO dtoCaixaEnviado = pedidoService.processarPedidoMobile(payload);

            assertThat(dtoCaixaEnviado.total()).isEqualByComparingTo(valorFinanceiroEsperado);
        }
    }
}