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
import java.util.Arrays;
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
    private Produto prodA;
    private Produto prodB;
    private UUID clienteId;
    private UUID pedidoId;
    private UUID prodAId;
    private UUID prodBId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();
        pedidoId = UUID.randomUUID();
        prodAId = UUID.randomUUID();
        prodBId = UUID.randomUUID();

        cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setNome("Maria Santos");

        prodA = new Produto(); prodA.setId(prodAId); prodA.setPreco(new BigDecimal("10.00")); prodA.setNome("X-Bacon");
        prodB = new Produto(); prodB.setId(prodBId); prodB.setPreco(new BigDecimal("20.00")); prodB.setNome("X-Tudo");

        carrinho = new Carrinho();
        carrinho.setCliente(cliente);
        carrinho.setItens(new ArrayList<>());

        ItemCarrinho item1 = new ItemCarrinho(); item1.setProduto(prodA); item1.setQuantidade(2); item1.setObservacao("Sem cebola");
        ItemCarrinho item2 = new ItemCarrinho(); item2.setProduto(prodB); item2.setQuantidade(3);
        carrinho.getItens().add(item1);
        carrinho.getItens().add(item2);

        pedidoPadrao = new Pedido();
        pedidoPadrao.setId(pedidoId);
        pedidoPadrao.setCliente(cliente);
        pedidoPadrao.setStatus(StatusPedido.RECEBIDO);
        pedidoPadrao.setTipo(TipoPedido.DELIVERY);
        pedidoPadrao.setTotal(new BigDecimal("80.00"));
        pedidoPadrao.setDataHora(LocalDateTime.now());
        pedidoPadrao.setNumeroPedido("TEST1");
    }

    // ==========================================
    // FLUXO ORIGINAL: CHECKOUT VIA CARRINHO (APP)
    // ==========================================

    @Test
    @DisplayName("Deve finalizar pedido com sucesso lendo a tabela de Carrinho (Fluxo do Aplicativo)")
    void deveFinalizarPedidoViaCarrinhoComSucesso() {
        CheckoutRequestDTO dtoApp = new CheckoutRequestDTO(
                clienteId, TipoPedido.DELIVERY, "Rua A, 123", null, "Sem observações",
                null, null, FormaPagamento.CREDITO, null, null
        );

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoApp);

        // Corrigido para comparação segura de BigDecimal
        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(carrinho.getItens()).isEmpty();
        verify(carrinhoRepository, times(1)).save(carrinho);
    }

    // ==========================================
    // FLUXO NOVO: CHECKOUT DIRETO DO PDV (BALCÃO)
    // ==========================================

    @Test
    @DisplayName("Deve finalizar venda rápida AVULSO no balcão sem cliente cadastrado no PIX")
    void deveFinalizarVendaRapidaAvulsaNoPixComSucesso() {
        List<ItemPedidoRequestDTO> itensDTo = List.of(
                new ItemPedidoRequestDTO(prodAId, 2, "Bem passado"),
                new ItemPedidoRequestDTO(prodBId, 1, null)
        );

        CheckoutRequestDTO dtoPdvAvulso = new CheckoutRequestDTO(
                null, TipoPedido.RETIRADA, null, null, "Cliente com pressa",
                null, null, FormaPagamento.PIX, null, itensDTo
        );

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));
        when(produtoRepository.findById(prodBId)).thenReturn(Optional.of(prodB));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoPdvAvulso);

        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(resultado.clienteNome()).isNull();
        verify(carrinhoRepository, never()).findByClienteId(any());
    }

    @Test
    @DisplayName("Deve finalizar venda rápida SIMPLES salvando o Nome e o Telefone do cliente no balcão")
    void deveFinalizarVendaRapidaSimplesComNomeETelefoneComSucesso() {
        List<ItemPedidoRequestDTO> itensDTo = List.of(new ItemPedidoRequestDTO(prodAId, 1, null));

        CheckoutRequestDTO dtoPdvSimples = new CheckoutRequestDTO(
                null, TipoPedido.RETIRADA, null, null, null,
                "Carlos Caipira", "16999998888", FormaPagamento.DEBITO, null, itensDTo
        );

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoPdvSimples);

        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(resultado.clienteNome()).isEqualTo("Carlos Caipira");
    }

    @Test
    @DisplayName("Deve finalizar venda rápida FIDELIDADE vinculando o Cliente Cadastrado do banco de dados")
    void deveFinalizarVendaRapidaFidelidadeComClienteCadastradoComSucesso() {
        List<ItemPedidoRequestDTO> itensDTo = List.of(new ItemPedidoRequestDTO(prodBId, 2, null));

        CheckoutRequestDTO dtoPdvFidelidade = new CheckoutRequestDTO(
                clienteId, TipoPedido.RETIRADA, null, null, "Cliente Especial",
                null, null, FormaPagamento.DINHEIRO, new BigDecimal("50.00"), itensDTo
        );

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
        when(produtoRepository.findById(prodBId)).thenReturn(Optional.of(prodB));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoPdvFidelidade);

        // Corrigido para comparação segura de BigDecimal
        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(resultado.clienteNome()).isEqualTo("Maria Santos");
        verify(clienteRepository, times(1)).findById(clienteId);
    }

    @Test
    @DisplayName("Deve garantir o cálculo do Preço Blindado estático na venda do balcão")
    void deveCalcularTotalECopiarPrecoBlindadoEmVendaRapida() {
        List<ItemPedidoRequestDTO> itensDTo = List.of(new ItemPedidoRequestDTO(prodAId, 5, null));
        CheckoutRequestDTO dtoPdv = new CheckoutRequestDTO(
                null, TipoPedido.RETIRADA, null, null, null,
                null, null, FormaPagamento.PIX, null, itensDTo
        );

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoPdv);

        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ==========================================
    // EXCEÇÕES E REGRAS DE NEGÓCIO (VALIDAÇÕES)
    // ==========================================

    @Test
    @DisplayName("Deve lançar exceção ao tentar finalizar qualquer venda com o caixa fechado")
    void deveLancarExcecaoComCaixaFechado() {
        CheckoutRequestDTO dto = new CheckoutRequestDTO(clienteId, TipoPedido.RETIRADA, null, null, null, null, null, FormaPagamento.PIX, null, null);
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> pedidoService.finalizarPedido(dto));
        assertThat(ex.getMessage()).contains("estabelecimento está fechado");
    }

    @Test
    @DisplayName("Deve lançar exceção quando o cliente fidelidade informado não existir no banco")
    void deveLancarExcecaoClienteFidelidadeInexistente() {
        List<ItemPedidoRequestDTO> itensDTo = List.of(new ItemPedidoRequestDTO(prodAId, 1, null));
        CheckoutRequestDTO dto = new CheckoutRequestDTO(clienteId, TipoPedido.RETIRADA, null, null, null, null, null, FormaPagamento.PIX, null, itensDTo);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(clienteRepository.findById(clienteId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pedidoService.finalizarPedido(dto));
    }

    @Test
    @DisplayName("Deve lançar exceção no fluxo do app se o carrinho do cliente não for localizado")
    void deveLancarExcecaoSemCarrinhoNoFluxoApp() {
        CheckoutRequestDTO dtoApp = new CheckoutRequestDTO(clienteId, TipoPedido.DELIVERY, null, null, null, null, null, FormaPagamento.PIX, null, null);

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pedidoService.finalizarPedido(dtoApp));
    }

    // ==========================================
    // TESTES DE MONITOR, STATUS E SEGURANÇA
    // ==========================================

    @Test
    @DisplayName("Deve buscar pedido por ID com sucesso")
    void deveBuscarPedidoPorId() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        PedidoResponseDTO resultado = pedidoService.buscarPorId(pedidoId);

        // Corrigido: Seu DTO retorna objeto UUID nativo, não String
        assertThat(resultado.id()).isEqualTo(pedidoId);
    }

    @Test
    @DisplayName("Deve listar apenas pedidos ativos no monitor da cozinha")
    void deveListarAtivosMonitor() {
        when(pedidoRepository.findByStatusInOrderByDataHoraAsc(anyList())).thenReturn(List.of(pedidoPadrao));
        List<PedidoResponseDTO> resultado = pedidoService.listarPedidosAtivosMonitor();
        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("Deve atualizar status do pedido com sucesso")
    void deveAtualizarStatusComSucesso() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoStatusRequestDTO novoStatus = new PedidoStatusRequestDTO(StatusPedido.EM_PREPARO);
        PedidoResponseDTO resultado = pedidoService.atualizarStatus(pedidoId, novoStatus);

        // Corrigido: Seu DTO retorna o Enum original StatusPedido, não String
        assertThat(resultado.status()).isEqualTo(StatusPedido.EM_PREPARO);
    }

    @Test
    @DisplayName("Deve impedir estritamente a exclusão física de registros do banco")
    void deveImpedirExclusaoFisica() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> pedidoService.excluirFisicamente(pedidoId));
        assertThat(ex.getMessage()).contains("pedidos não podem ser excluídos");
    }

    // ==========================================
    // NOVOS: COBERTURA COMPLETA DE MODALIDADES
    // ==========================================

    @Test
    @DisplayName("Deve finalizar venda direta no formato MESA gravando o número da mesa com sucesso")
    void deveFinalizarVendaDiretaModalidadeMesaComSucesso() {
        List<ItemPedidoRequestDTO> itensDTo = List.of(new ItemPedidoRequestDTO(prodAId, 1, null));

        CheckoutRequestDTO dtoMesa = new CheckoutRequestDTO(
                null, TipoPedido.MESA, null, 4, "Deixar a conta aberta",
                null, null, FormaPagamento.CREDITO, null, itensDTo
        );

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodAId)).thenReturn(Optional.of(prodA));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoMesa);

        // Corrigido aqui: Removido o .clear() intruso!
        assertThat(resultado.tipo()).isEqualTo(TipoPedido.MESA);
        assertThat(resultado.numeroMesa()).isEqualTo(4);
    }

    @Test
    @DisplayName("Deve finalizar venda direta no formato DELIVERY via Balcão gravando o endereço com sucesso")
    void deveFinalizarVendaDiretaModalidadeDeliveryComSucesso() {
        List<ItemPedidoRequestDTO> itensDTo = List.of(new ItemPedidoRequestDTO(prodBId, 1, null));

        CheckoutRequestDTO dtoDeliveryBalcao = new CheckoutRequestDTO(
                null, TipoPedido.DELIVERY, "Av. Dos Lanches, 999", null, "Entregar nas fundas",
                "Cliente Telefone", "16988887777", FormaPagamento.PIX, null, itensDTo
        );

        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(produtoRepository.findById(prodBId)).thenReturn(Optional.of(prodB));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(dtoDeliveryBalcao);

        assertThat(resultado.tipo()).isEqualTo(TipoPedido.DELIVERY);
        assertThat(resultado.enderecoEntrega()).isEqualTo("Av. Dos Lanches, 999");
    }

}