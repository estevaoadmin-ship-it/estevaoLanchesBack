package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    private Cliente clientePadrao;

    @BeforeEach
    void setUp() {
        clientePadrao = new Cliente();
        clientePadrao.setNome("Cliente Teste");
        clientePadrao.setEmail("teste@email.com");
        clientePadrao.setCpf("12345678901");
        clienteRepository.save(clientePadrao);
    }

    private Pedido criarPedido(LocalDateTime dataHora, StatusPedido status, String numeroPedido) {
        Pedido pedido = new Pedido();
        pedido.setCliente(clientePadrao);
        pedido.setDataHora(dataHora);
        pedido.setStatus(status);
        pedido.setTipo(TipoPedido.DELIVERY);
        pedido.setTotal(new BigDecimal("50.00"));
        pedido.setNumeroPedido(numeroPedido);
        return pedidoRepository.save(pedido);
    }

    // --- Testes 22 e 23: Histórico do Cliente ---
    @Test
    @DisplayName("Teste 22: Deve buscar pedidos do cliente ordenados por data decrescente")
    void deveBuscarPedidosDoClienteOrdenadosPorDataDecrescente() {
        criarPedido(LocalDateTime.now().minusDays(2), StatusPedido.FINALIZADO, "PED01");
        criarPedido(LocalDateTime.now().minusDays(1), StatusPedido.FINALIZADO, "PED02"); // Mais recente

        List<Pedido> pedidos = pedidoRepository.findByClienteIdOrderByDataHoraDesc(clientePadrao.getId());

        assertThat(pedidos).hasSize(2);
        assertThat(pedidos.get(0).getNumeroPedido()).isEqualTo("PED02"); // O mais recente deve vir primeiro
    }

    @Test
    @DisplayName("Teste 23: Deve retornar lista vazia quando cliente não possuir pedidos")
    void deveRetornarVazioQuandoClienteNaoPossuirPedidos() {
        List<Pedido> pedidos = pedidoRepository.findByClienteIdOrderByDataHoraDesc(clientePadrao.getId());
        assertThat(pedidos).isEmpty();
    }

    // --- Testes 24, 25 e 26: Monitor da Cozinha/Caixa ---
    @Test
    @DisplayName("Testes 24 e 25: Deve buscar pedidos pelos status ordenados por data crescente")
    void deveBuscarPedidosPorStatusOrdenadosDataCrescente() {
        criarPedido(LocalDateTime.now().minusMinutes(30), StatusPedido.RECEBIDO, "PED01"); // Mais antigo
        criarPedido(LocalDateTime.now().minusMinutes(10), StatusPedido.EM_PREPARO, "PED02");
        criarPedido(LocalDateTime.now(), StatusPedido.FINALIZADO, "PED03"); // Fora do filtro

        List<StatusPedido> statusAtivos = Arrays.asList(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARO);
        List<Pedido> pedidos = pedidoRepository.findByStatusInOrderByDataHoraAsc(statusAtivos);

        assertThat(pedidos).hasSize(2);
        assertThat(pedidos.get(0).getNumeroPedido()).isEqualTo("PED01"); // O mais antigo deve ser o primeiro da fila
        assertThat(pedidos).noneMatch(p -> p.getStatus() == StatusPedido.FINALIZADO);
    }

    @Test
    @DisplayName("Teste 26: Deve retornar lista vazia quando não existir pedido com os status informados")
    void deveRetornarVazioSeNenhumStatusCorresponder() {
        criarPedido(LocalDateTime.now(), StatusPedido.FINALIZADO, "PED01");

        List<StatusPedido> statusAtivos = Arrays.asList(StatusPedido.RECEBIDO);
        List<Pedido> pedidos = pedidoRepository.findByStatusInOrderByDataHoraAsc(statusAtivos);

        assertThat(pedidos).isEmpty();
    }

    // --- Testes 27 e 28: Busca pelo Número do Pedido ---
    @Test
    @DisplayName("Teste 27: Deve buscar pedido pelo número do pedido")
    void deveBuscarPedidoPeloNumero() {
        criarPedido(LocalDateTime.now(), StatusPedido.RECEBIDO, "XYZ99");
        Optional<Pedido> resultado = pedidoRepository.findByNumeroPedido("XYZ99");
        assertThat(resultado).isPresent();
    }

    @Test
    @DisplayName("Teste 28: Deve retornar Optional vazio quando número do pedido não existir")
    void naoDeveEncontrarPedidoComNumeroInexistente() {
        Optional<Pedido> resultado = pedidoRepository.findByNumeroPedido("00000");
        assertThat(resultado).isEmpty();
    }
}