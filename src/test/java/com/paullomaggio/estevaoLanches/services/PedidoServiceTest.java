package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoStatusRequestDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
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

    @InjectMocks
    private PedidoService pedidoService;

    private Cliente cliente;
    private Carrinho carrinho;
    private Pedido pedidoPadrao;
    private CheckoutRequestDTO checkoutDTO;
    private UUID clienteId;
    private UUID pedidoId;

    @BeforeEach
    void setUp() {
        clienteId = UUID.randomUUID();
        pedidoId = UUID.randomUUID();

        cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setNome("Maria Santos");

        Produto prodA = new Produto(); prodA.setPreco(new BigDecimal("10.00")); prodA.setNome("X-Bacon");
        Produto prodB = new Produto(); prodB.setPreco(new BigDecimal("20.00")); prodB.setNome("X-Tudo");

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

        checkoutDTO = new CheckoutRequestDTO(clienteId, TipoPedido.DELIVERY, "Rua A, 123", null, "Troco para R$100");
    }

    // ==========================================
    // TESTES DE FINALIZAÇÃO (CHECKOUT)
    // ==========================================

    @Test
    @DisplayName("Teste 1, 5 e 6: Deve finalizar pedido com sucesso, calculando total e copiando itens")
    void deveFinalizarPedidoComSucesso() {
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.finalizarPedido(checkoutDTO);

        // Valida T5: Cálculo do total (2 * 10) + (3 * 20) = 80
        assertThat(resultado.total()).isEqualByComparingTo(new BigDecimal("80.00"));

        // Valida T6: Cópia dos itens usando os novos nomes do DTO
        assertThat(resultado.itens()).hasSize(2);
        assertThat(resultado.itens().get(0).produtoNome()).isEqualTo("X-Bacon");
        assertThat(resultado.itens().get(0).quantidade()).isEqualTo(2);
        assertThat(resultado.itens().get(0).observacaoItem()).isEqualTo("Sem cebola");

        // Validação crucial do "Preço Blindado"
        assertThat(resultado.itens().get(0).precoUnitarioHistorico()).isEqualByComparingTo(new BigDecimal("10.00"));

        // Valida T1: Carrinho limpo
        assertThat(carrinho.getItens()).isEmpty();
        verify(carrinhoRepository, times(1)).save(carrinho);
    }

    @Test
    @DisplayName("Teste 2: Deve lançar exceção ao tentar finalizar com caixa fechado")
    void deveLancarExcecaoComCaixaFechado() {
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> pedidoService.finalizarPedido(checkoutDTO));
        assertThat(ex.getMessage()).contains("estabelecimento está fechado");
    }

    @Test
    @DisplayName("Teste 3: Deve lançar exceção quando carrinho não existir")
    void deveLancarExcecaoSemCarrinho() {
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pedidoService.finalizarPedido(checkoutDTO));
    }

    @Test
    @DisplayName("Teste 4: Deve lançar exceção quando carrinho estiver vazio")
    void deveLancarExcecaoCarrinhoVazio() {
        carrinho.getItens().clear(); // Esvazia o carrinho
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);
        when(carrinhoRepository.findByClienteId(clienteId)).thenReturn(Optional.of(carrinho));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> pedidoService.finalizarPedido(checkoutDTO));
        assertThat(ex.getMessage()).contains("carrinho está vazio");
    }

    // ==========================================
    // TESTES DE BUSCA E LISTAGEM
    // ==========================================

    @Test
    @DisplayName("Teste 7: Deve buscar pedido por ID com sucesso")
    void deveBuscarPedidoPorId() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        PedidoResponseDTO resultado = pedidoService.buscarPorId(pedidoId);
        assertThat(resultado.id()).isEqualTo(pedidoId);
    }

    @Test
    @DisplayName("Teste 8: Deve lançar exceção ao buscar pedido inexistente")
    void deveLancarExcecaoAoBuscarIdInexistente() {
        when(pedidoRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.buscarPorId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Teste 9 e 10: Listar todos os pedidos")
    void deveListarTodosOsPedidos() {
        when(pedidoRepository.findAll()).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarTodos()).hasSize(1);

        when(pedidoRepository.findAll()).thenReturn(new ArrayList<>());
        assertThat(pedidoService.listarTodos()).isEmpty();
    }

    @Test
    @DisplayName("Teste 11 e 12: Listar histórico do cliente")
    void deveListarHistoricoCliente() {
        when(pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId)).thenReturn(List.of(pedidoPadrao));
        assertThat(pedidoService.listarHistoricoCliente(clienteId)).hasSize(1);

        when(pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId)).thenReturn(new ArrayList<>());
        assertThat(pedidoService.listarHistoricoCliente(clienteId)).isEmpty();
    }

    @Test
    @DisplayName("Teste 13: Deve listar apenas pedidos ativos do monitor")
    void deveListarAtivosMonitor() {
        when(pedidoRepository.findByStatusInOrderByDataHoraAsc(anyList())).thenReturn(List.of(pedidoPadrao));
        List<PedidoResponseDTO> resultado = pedidoService.listarPedidosAtivosMonitor();
        assertThat(resultado).hasSize(1);
    }

    // ==========================================
    // TESTES DE ATUALIZAÇÃO DE STATUS
    // ==========================================

    @Test
    @DisplayName("Teste 14: Deve atualizar status do pedido com sucesso")
    void deveAtualizarStatusComSucesso() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoStatusRequestDTO novoStatus = new PedidoStatusRequestDTO(StatusPedido.EM_PREPARO);
        PedidoResponseDTO resultado = pedidoService.atualizarStatus(pedidoId, novoStatus);

        assertThat(resultado.status()).isEqualTo(StatusPedido.EM_PREPARO);
    }

    @Test
    @DisplayName("Teste 15: Deve lançar exceção ao atualizar pedido já finalizado")
    void naoDeveAtualizarPedidoFinalizado() {
        pedidoPadrao.setStatus(StatusPedido.FINALIZADO);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        assertThrows(BusinessRuleException.class, () ->
                pedidoService.atualizarStatus(pedidoId, new PedidoStatusRequestDTO(StatusPedido.EM_ROTA))
        );
    }

    @Test
    @DisplayName("Teste 16: Deve lançar exceção ao atualizar pedido cancelado")
    void naoDeveAtualizarPedidoCancelado() {
        pedidoPadrao.setStatus(StatusPedido.CANCELADO);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        assertThrows(BusinessRuleException.class, () ->
                pedidoService.atualizarStatus(pedidoId, new PedidoStatusRequestDTO(StatusPedido.RECEBIDO))
        );
    }

    @Test
    @DisplayName("Teste 17: Deve lançar exceção ao atualizar status de pedido inexistente")
    void naoDeveAtualizarStatusInexistente() {
        when(pedidoRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
                pedidoService.atualizarStatus(UUID.randomUUID(), new PedidoStatusRequestDTO(StatusPedido.PRONTO))
        );
    }

    // ==========================================
    // TESTES DE CANCELAMENTO E EXCLUSÃO
    // ==========================================

    @Test
    @DisplayName("Teste 18: Deve cancelar pedido com sucesso")
    void deveCancelarPedidoComSucesso() {
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        PedidoResponseDTO resultado = pedidoService.cancelarPedido(pedidoId);
        assertThat(resultado.status()).isEqualTo(StatusPedido.CANCELADO);
    }

    @Test
    @DisplayName("Teste 19: Deve lançar exceção ao cancelar pedido já finalizado")
    void naoDeveCancelarPedidoFinalizado() {
        pedidoPadrao.setStatus(StatusPedido.FINALIZADO);
        when(pedidoRepository.findById(pedidoId)).thenReturn(Optional.of(pedidoPadrao));

        assertThrows(BusinessRuleException.class, () -> pedidoService.cancelarPedido(pedidoId));
    }

    @Test
    @DisplayName("Teste 20: Deve lançar exceção ao cancelar pedido inexistente")
    void naoDeveCancelarPedidoInexistente() {
        when(pedidoRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> pedidoService.cancelarPedido(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Teste 21: Deve impedir exclusão física do banco de dados")
    void deveImpedirExclusaoFisica() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> pedidoService.excluirFisicamente(pedidoId));
        assertThat(ex.getMessage()).contains("pedidos não podem ser excluídos");
    }
}