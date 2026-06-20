package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.dtos.ProdutoRankingDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PedidoRepositoryTest {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Cliente clientePadrao;
    private Produto produtoQuePrepara;
    private Produto produtoProntoBalcao;

    @BeforeEach
    void setUp() {
        clientePadrao = new Cliente();
        clientePadrao.setNome("Cliente Teste");
        clientePadrao.setEmail("teste@email.com");
        clientePadrao.setCpf("12345678901");
        clienteRepository.save(clientePadrao);

        Categoria categoria = new Categoria();
        categoria.setNome("Geral");
        entityManager.persist(categoria);

        produtoQuePrepara = new Produto();
        produtoQuePrepara.setNome("X-Bacon");
        produtoQuePrepara.setDescricao("Lanche");
        produtoQuePrepara.setPreco(new BigDecimal("25.00"));
        produtoQuePrepara.setStatus(StatusProduto.values()[0]);
        produtoQuePrepara.setIsCombo(false);
        produtoQuePrepara.setPrecisaPreparo(true);
        produtoQuePrepara.setCategoria(categoria);

        produtoProntoBalcao = new Produto();
        produtoProntoBalcao.setNome("Coca Lata");
        produtoProntoBalcao.setDescricao("Bebida");
        produtoProntoBalcao.setPreco(new BigDecimal("6.00"));
        produtoProntoBalcao.setStatus(StatusProduto.values()[0]);
        produtoProntoBalcao.setIsCombo(false);
        produtoProntoBalcao.setPrecisaPreparo(false);
        produtoProntoBalcao.setCategoria(categoria);

        entityManager.persist(produtoQuePrepara);
        entityManager.persist(produtoProntoBalcao);
        entityManager.flush();
    }

    private Pedido criarPedido(LocalDateTime dataHora, StatusPedido status, String numeroPedido, FormaPagamento forma, BigDecimal total, Produto produto) {
        Pedido pedido = new Pedido();
        pedido.setCliente(clientePadrao);
        pedido.setDataHora(dataHora);
        pedido.setStatus(status);
        pedido.setStatusFinanceiro(forma != null ? StatusFinanceiro.PAGO : StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setTipo(TipoPedido.DELIVERY);
        pedido.setTotal(total);
        pedido.setNumeroPedido(numeroPedido);
        pedido.setFormaPagamento(forma);
        pedido.setItens(new ArrayList<>());

        ItemPedido item = new ItemPedido();
        item.setProduto(produto);
        item.setQuantidade(1);
        item.setPrecoUnitario(total);
        item.setPedido(pedido);

        pedido.getItens().add(item);

        return pedidoRepository.save(pedido);
    }

    private Pedido criarPedidoPadrao(LocalDateTime dataHora, StatusPedido status, String numeroPedido) {
        return criarPedido(dataHora, status, numeroPedido, FormaPagamento.DINHEIRO, new BigDecimal("50.00"), produtoQuePrepara);
    }

    @Test
    @DisplayName("Teste 22: Deve buscar pedidos do cliente ordenados por data decrescente")
    void deveBuscarPedidosDoClienteOrdenadosPorDataDecrescente() {
        criarPedidoPadrao(LocalDateTime.now().minusDays(2), StatusPedido.FINALIZADO, "PED01");
        criarPedidoPadrao(LocalDateTime.now().minusDays(1), StatusPedido.FINALIZADO, "PED02");

        List<Pedido> pedidos = pedidoRepository.findByClienteIdOrderByDataHoraDesc(clientePadrao.getId());

        assertThat(pedidos).hasSize(2);
        assertThat(pedidos.get(0).getNumeroPedido()).isEqualTo("PED02");
    }

    @Test
    @DisplayName("Teste 23: Deve retornar lista vazia quando cliente não possuir pedidos")
    void deveRetornarVazioQuandoClienteNaoPossuirPedidos() {
        List<Pedido> pedidos = pedidoRepository.findByClienteIdOrderByDataHoraDesc(clientePadrao.getId());
        assertThat(pedidos).isEmpty();
    }

    @Test
    @DisplayName("Testes 24 e 25: Deve buscar pedidos pelos status ordenados por data crescente")
    void deveBuscarPedidosPorStatusOrdenadosDataCrescente() {
        criarPedidoPadrao(LocalDateTime.now().minusMinutes(30), StatusPedido.RECEBIDO, "PED01");
        criarPedidoPadrao(LocalDateTime.now().minusMinutes(10), StatusPedido.EM_PREPARO, "PED02");
        criarPedidoPadrao(LocalDateTime.now(), StatusPedido.FINALIZADO, "PED03");

        List<StatusPedido> statusAtivos = Arrays.asList(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARO);
        List<Pedido> pedidos = pedidoRepository.findByStatusInOrderByDataHoraAsc(statusAtivos);

        assertThat(pedidos).hasSize(2);
        assertThat(pedidos.get(0).getNumeroPedido()).isEqualTo("PED01");
    }

    @Test
    @DisplayName("Teste 26: Deve retornar lista vazia quando não existir pedido com os status informados")
    void deveRetornarVazioSeNenhumStatusCorresponder() {
        criarPedidoPadrao(LocalDateTime.now(), StatusPedido.FINALIZADO, "PED01");

        List<StatusPedido> statusAtivos = Arrays.asList(StatusPedido.RECEBIDO);
        List<Pedido> pedidos = pedidoRepository.findByStatusInOrderByDataHoraAsc(statusAtivos);

        assertThat(pedidos).isEmpty();
    }

    @Test
    @DisplayName("Teste 27: Deve buscar pedido pelo número do pedido")
    void deveBuscarPedidoPeloNumero() {
        criarPedidoPadrao(LocalDateTime.now(), StatusPedido.RECEBIDO, "XYZ99");
        Optional<Pedido> resultado = pedidoRepository.findByNumeroPedido("XYZ99");
        assertThat(resultado).isPresent();
    }

    @Test
    @DisplayName("Teste 28: Deve retornar Optional vazio quando número do pedido não existir")
    void naoDeveEncontrarPedidoComNumeroInexistente() {
        Optional<Pedido> resultado = pedidoRepository.findByNumeroPedido("00000");
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("CT-REPO-KPI-001: Deve contar na esteira apenas pedidos que contenham itens para preparar")
    void deveContarPedidosEmEsteiraComPrecisao() {
        criarPedido(LocalDateTime.now(), StatusPedido.RECEBIDO, "P1", FormaPagamento.PIX, new BigDecimal("25.00"), produtoQuePrepara);
        criarPedido(LocalDateTime.now(), StatusPedido.EM_PREPARO, "P2", FormaPagamento.PIX, new BigDecimal("25.00"), produtoQuePrepara);
        criarPedido(LocalDateTime.now(), StatusPedido.RECEBIDO, "P3", FormaPagamento.PIX, new BigDecimal("6.00"), produtoProntoBalcao);

        long ativos = pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO);

        assertThat(ativos).isEqualTo(2);
    }

    @Test
    @DisplayName("CT-REPO-KPI-002: Deve faturar independentemente se o produto exige ou não preparo")
    void deveCalcularFaturamentoFatiadoPorTurno() {
        LocalDateTime inicioTurno = LocalDateTime.now().minusHours(1);

        criarPedido(LocalDateTime.now(), StatusPedido.FINALIZADO, "A1", FormaPagamento.DINHEIRO, new BigDecimal("100.00"), produtoQuePrepara);
        criarPedido(LocalDateTime.now(), StatusPedido.FINALIZADO, "A2", FormaPagamento.DINHEIRO, new BigDecimal("50.00"), produtoProntoBalcao);

        BigDecimal totalDinheiro = pedidoRepository.somarFaturamentoPorTurnoEForma(inicioTurno, FormaPagamento.DINHEIRO, StatusPedido.FINALIZADO);

        assertThat(totalDinheiro).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("CT-REPO-BI-003: Deve rodar queries do painel gerencial no H2 sem estourar erros de sintaxe JPQL")
    void deveExecutarQueriesDoRelatorioComSucesso() {
        LocalDateTime inicio = LocalDateTime.now().minusDays(1);
        LocalDateTime fim = LocalDateTime.now().plusDays(1);

        criarPedido(LocalDateTime.now(), StatusPedido.FINALIZADO, "B1", FormaPagamento.PIX, new BigDecimal("35.00"), produtoQuePrepara);
        entityManager.flush();

        List<Pedido> relatorio = pedidoRepository.buscarPedidosParaRelatorio(inicio, fim);
        List<com.paullomaggio.estevaoLanches.dtos.MeioPagamentoItemDTO> pagamentos =
                pedidoRepository.somarFaturamentoPorMeioPagamento(inicio, fim, StatusPedido.FINALIZADO);

        assertThat(relatorio).isNotEmpty();
        assertThat(pagamentos).isNotEmpty();
    }

    // =========================================================================
    // 🆕 VALIDAÇÃO DO BI DO GRUPO (RANKING DE PRODUTOS CORRIGIDO)
    // =========================================================================

    @Test
    @DisplayName("CT-REPO-BI-004: Deve processar o Ranking de Produtos via JPQL agregando os itens por agrupamento sem falhas de sintaxe")
    void deveBuscarTopProdutosOrdenadosPorMaisVendidosSemErros() {
        LocalDateTime inicio = LocalDateTime.now().minusDays(1);
        LocalDateTime fim = LocalDateTime.now().plusDays(1);

        criarPedido(LocalDateTime.now(), StatusPedido.FINALIZADO, "K1", FormaPagamento.PIX, new BigDecimal("25.00"), produtoQuePrepara);
        criarPedido(LocalDateTime.now(), StatusPedido.FINALIZADO, "K2", FormaPagamento.PIX, new BigDecimal("25.00"), produtoQuePrepara);
        criarPedido(LocalDateTime.now(), StatusPedido.FINALIZADO, "K3", FormaPagamento.PIX, new BigDecimal("6.00"), produtoProntoBalcao);

        entityManager.flush();

        List<ProdutoRankingDTO> ranking = pedidoRepository.buscarTopProdutosJPQL(inicio, fim, StatusPedido.FINALIZADO, PageRequest.of(0, 10));

        assertThat(ranking).hasSize(2);

        // 🚀 CORRIGIDO: Modificado de sintaxe de Record para Getters clássicos gerados pelo Lombok @Data
        assertThat(ranking.get(0).getNomeProduto()).isEqualTo("X-Bacon");
        assertThat(ranking.get(0).getQuantidadeVendida()).isEqualTo(2L);
    }
}