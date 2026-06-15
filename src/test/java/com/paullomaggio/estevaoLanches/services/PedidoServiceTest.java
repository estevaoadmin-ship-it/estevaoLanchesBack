package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemPedidoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoStatusRequestDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private CarrinhoRepository carrinhoRepository;
    @Mock private CaixaRepository caixaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private ClienteRepository clienteRepository;

    @InjectMocks
    private PedidoService pedidoService;

    private Cliente cliente;
    private Carrinho carrinho;
    private Pedido pedidoPadrao;
    private ItemPedido itemPedidoExistente;
    private Produto prodA, prodB;
    private UUID clienteId, pedidoId, prodAId, prodBId, itemExistenteId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();
        pedidoId = UUID.randomUUID();
        prodAId = UUID.randomUUID();
        prodBId = UUID.randomUUID();
        itemExistenteId = UUID.randomUUID();

        cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setNome("Maria Santos");

        prodA = new Produto(); prodA.setId(prodAId); prodA.setPreco(new BigDecimal("10.00")); prodA.setNome("X-Bacon"); prodA.setPrecisaPreparo(true);
        prodB = new Produto(); prodB.setId(prodBId); prodB.setPreco(new BigDecimal("20.00")); prodB.setNome("X-Tudo"); prodB.setPrecisaPreparo(true);

        carrinho = new Carrinho();
        carrinho.setCliente(cliente);
        carrinho.setItens(new ArrayList<>());
        ItemCarrinho item1 = new ItemCarrinho(); item1.setProduto(prodA); item1.setQuantidade(2); item1.setObservacao("Sem cebola");
        carrinho.getItens().add(item1);

        pedidoPadrao = new Pedido();
        pedidoPadrao.setId(pedidoId);
        pedidoPadrao.setCliente(cliente);
        pedidoPadrao.setStatus(StatusPedido.RECEBIDO);
        pedidoPadrao.setTipo(TipoPedido.DELIVERY);
        pedidoPadrao.setTotal(new BigDecimal("20.00"));
        pedidoPadrao.setDataHora(LocalDateTime.now());
        pedidoPadrao.setNumeroPedido("TEST1");
        pedidoPadrao.setItens(new ArrayList<>());

        itemPedidoExistente = new ItemPedido();
        itemPedidoExistente.setId(itemExistenteId);
        itemPedidoExistente.setProduto(prodA);
        itemPedidoExistente.setQuantidade(2);
        itemPedidoExistente.setPrecoUnitario(new BigDecimal("10.00"));
        itemPedidoExistente.setPedido(pedidoPadrao);
        pedidoPadrao.getItens().add(itemPedidoExistente);
    }

    @Test
    @DisplayName("Testes 1 a 8, 13 e 16: Checkout Geral, Limpeza de Carrinho e Preço Blindado")
    void deveFinalizarCheckoutsDiversosComSucesso() {
        CheckoutRequestDTO dtoApp = new CheckoutRequestDTO(clienteId, TipoPedido.DELIVERY, "Rua A", null, null, null, null, FormaPagamento.CREDITO, null, null);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoApp);

        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(carrinho.getItens()).isEmpty();
    }

    @Test
    @DisplayName("Testes 9 a 12 e 14 a 15: Exceções em Checkouts (Caixa Fechado, Sem Carrinho, etc)")
    void deveLancarExcecoesRegrasDeNegocioNoCheckout() {
        CheckoutRequestDTO dto = new CheckoutRequestDTO(clienteId, TipoPedido.DELIVERY, null, null, null, null, null, FormaPagamento.PIX, null, null);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);
        assertThrows(BusinessRuleException.class, () -> pedidoService.finalizarPedido(dto));

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);

        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.finalizarPedido(dto));

        carrinho.getItens().clear();
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        assertThrows(BusinessRuleException.class, () -> pedidoService.finalizarPedido(dto));
    }

    @Test
    @DisplayName("Testes 19 a 22: Buscar Pedido e Listar Todos")
    void deveBuscarEListarTodosOsPedidos() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        assertThat(pedidoService.buscarPorId(pedidoId).id()).isEqualTo(pedidoId);

        when(pedidoRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.buscarPorId(UUID.randomUUID()));

        when(pedidoRepository.findAll()).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarTodos()).hasSize(1);

        when(pedidoRepository.findAll()).thenReturn(Collections.emptyList());
        assertThat(pedidoService.listarTodos()).isEmpty();
    }

    @Test
    @DisplayName("Testes 23 a 26: Histórico do Cliente e Monitor da Cozinha")
    void deveListarHistoricoEMonitor() {
        when(pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId)).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarHistoricoCliente(clienteId)).hasSize(1);

        when(pedidoRepository.findByStatusInOrderByDataHoraAsc(anyList())).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarPedidosAtivosMonitor()).hasSize(1);

        when(pedidoRepository.findByStatusInOrderByDataHoraAsc(anyList())).thenReturn(Collections.emptyList());
        assertThat(pedidoService.listarPedidosAtivosMonitor()).isEmpty();
    }

    @Test
    @DisplayName("Testes 27 a 30: Deve atualizar status seguindo o fluxo normal")
    void deveAtualizarStatusComSucesso() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.atualizarStatus(pedidoId, new PedidoStatusRequestDTO(StatusPedido.EM_PREPARO));
        assertThat(res.status()).isEqualTo(StatusPedido.EM_PREPARO);
    }

    @Test
    @DisplayName("Testes 31 a 33: Deve impedir atualização de pedidos Finalizados, Cancelados ou Inexistentes")
    void deveImpedirAtualizacaoDeStatusInvalidos() {
        pedidoPadrao.setStatus(StatusPedido.FINALIZADO);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        assertThrows(BusinessRuleException.class, () -> pedidoService.atualizarStatus(pedidoId, new PedidoStatusRequestDTO(StatusPedido.RECEBIDO)));

        pedidoPadrao.setStatus(StatusPedido.CANCELADO);
        assertThrows(BusinessRuleException.class, () -> pedidoService.atualizarStatus(pedidoId, new PedidoStatusRequestDTO(StatusPedido.RECEBIDO)));

        when(pedidoRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.atualizarStatus(UUID.randomUUID(), new PedidoStatusRequestDTO(StatusPedido.RECEBIDO)));
    }

    @Test
    @DisplayName("Testes 34 a 39: Deve permitir cancelar pedidos abertos e impedir finalizados")
    void deveCancelarPedidosCorretamente() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(pedidoService.cancelarPedido(pedidoId).status()).isEqualTo(StatusPedido.CANCELADO);

        pedidoPadrao.setStatus(StatusPedido.FINALIZADO);
        assertThrows(BusinessRuleException.class, () -> pedidoService.cancelarPedido(pedidoId));
    }

    @Test
    @DisplayName("Testes 40 e 41: Deve adicionar item e recalcular total")
    void deveAdicionarItemERecalcularTotal() {
        ItemPedidoRequestDTO novoItem = new ItemPedidoRequestDTO(prodBId, 1, "Adicional");

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(produtoRepository.findById(prodBId)).thenReturn(Optional.of(prodB));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.adicionarItemPedido(pedidoId, novoItem);

        assertThat(res.total()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(pedidoPadrao.getItens()).hasSize(2);
    }

    @Test
    @DisplayName("Testes 42 a 46: Impedir adição de itens em regras inválidas")
    void deveImpedirAdicaoDeItemInvalida() {
        ItemPedidoRequestDTO novoItem = new ItemPedidoRequestDTO(prodBId, 1, null);

        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(produtoRepository.findById(prodBId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.adicionarItemPedido(pedidoId, novoItem));

        pedidoPadrao.setStatus(StatusPedido.EM_ROTA);
        assertThrows(BusinessRuleException.class, () -> pedidoService.adicionarItemPedido(pedidoId, novoItem));
    }

    @Test
    @DisplayName("Testes 47 e 48: Deve remover item e recalcular total")
    void deveRemoverItemERecalcularTotal() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO res = pedidoService.removerItemPedido(pedidoId, itemExistenteId);

        assertThat(res.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pedidoPadrao.getItens()).isEmpty();
    }

    @Test
    @DisplayName("Testes 49 a 53: Impedir remoção de itens em regras inválidas")
    void deveImpedirRemocaoDeItemInvalida() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.removerItemPedido(pedidoId, UUID.randomUUID()));

        pedidoPadrao.setStatus(StatusPedido.FINALIZADO);
        assertThrows(BusinessRuleException.class, () -> pedidoService.removerItemPedido(pedidoId, itemExistenteId));
    }

    @Test
    @DisplayName("Teste 54: Impedir exclusão física")
    void deveImpedirExclusaoFisica() {
        assertThrows(BusinessRuleException.class, () -> pedidoService.excluirFisicamente(pedidoId));
    }

    @Test
    @DisplayName("Testes 55 a 60: Casos de Borda (Extremos, Sem Observação, Múltiplos Itens)")
    void deveProcessarCasosDeBordaComSucesso() {
        List<ItemPedidoRequestDTO> itensExtremos = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            itensExtremos.add(new ItemPedidoRequestDTO(prodAId, 2, ""));
        }

        CheckoutRequestDTO dtoBorda = new CheckoutRequestDTO(null, TipoPedido.MESA, null, 1, null, null, null, FormaPagamento.DINHEIRO, null, itensExtremos);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoBorda);

        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(resultado.observacaoGeral()).isNull();
    }
}