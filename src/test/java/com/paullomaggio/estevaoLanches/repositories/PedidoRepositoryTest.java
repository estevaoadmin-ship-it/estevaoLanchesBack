package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("🧪 Testes de Integração de Banco de Dados — PedidoRepository")
class PedidoRepositoryTest {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Comanda comandaMestre;
    private Conta contaMestre;
    private Cliente clienteMestre;

    @BeforeEach
    void setUp() {
        // 1. Cria e persiste a Mesa estrutural
        Mesa mesa = new Mesa(null, UUID.randomUUID(), UUID.randomUUID(), 45, StatusMesa.LIVRE);
        mesa = entityManager.persist(mesa);

        // 2. Cria e persiste a Comanda mestre vinculada à mesa
        comandaMestre = new Comanda();
        comandaMestre.setMesa(mesa);
        comandaMestre.setStatus(StatusComanda.ABERTA);
        comandaMestre.setEmpresaId(UUID.randomUUID());
        comandaMestre.setFilialId(UUID.randomUUID());
        comandaMestre.setDataHoraAbertura(LocalDateTime.now());
        comandaMestre = entityManager.persist(comandaMestre);

        // 🎯 FIX DEFINITIVO: Instancia e persiste o cliente obrigatório para satisfazer a constraint de banco de dados
        clienteMestre = new Cliente();
        clienteMestre.setNome("CONSUMIDOR TESTE REPO");
        clienteMestre.setCpf("12345678901");
        clienteMestre.setEmail("consumidor.teste@estevaolanches.com");
        clienteMestre.setStatus(StatusCliente.ATIVO);
        clienteMestre.setEnderecos(new ArrayList<>());
        clienteMestre = entityManager.persist(clienteMestre);

        // 3. Cria e persiste a Conta vinculando a Comanda e o Cliente obrigatório
        contaMestre = new Conta();
        contaMestre.setComanda(comandaMestre);
        contaMestre.setCliente(clienteMestre); // 🛡️ Blindagem contra o erro de CLIENTE_ID NULL
        contaMestre.setNumeroConta(1);
        contaMestre.setPago(false);
        contaMestre.setValorTotal(BigDecimal.ZERO);
        contaMestre.setPedidos(new ArrayList<>());
        contaMestre = entityManager.persist(contaMestre);
    }

    @Test
    @DisplayName("Deve contar pedidos ativos na esteira operacional descartando excluídos")
    void deveContarPedidosEmEsteiraComPrecisao() {
        // Configuração estrutural dos lotes de pedidos para o teste de contagem do Caixa
        Pedido p1 = new Pedido(null, "NUM01", contaMestre, clienteMestre, null, LocalDateTime.now(), StatusPedido.RECEBIDO, StatusFinanceiro.AGUARDANDO_PAGAMENTO, TipoPedido.MESA, 45, "Rua A", new BigDecimal("20.00"), null, null, null, new ArrayList<>());
        Pedido p2 = new Pedido(null, "NUM02", contaMestre, clienteMestre, null, LocalDateTime.now(), StatusPedido.EM_PREPARO, StatusFinanceiro.AGUARDANDO_PAGAMENTO, TipoPedido.MESA, 45, "Rua A", new BigDecimal("35.00"), null, null, null, new ArrayList<>());
        Pedido p3 = new Pedido(null, "NUM03", contaMestre, clienteMestre, null, LocalDateTime.now(), StatusPedido.FINALIZADO, StatusFinanceiro.PAGO, TipoPedido.MESA, 45, "Rua A", new BigDecimal("15.00"), null, null, null, new ArrayList<>());

        pedidoRepository.save(p1);
        pedidoRepository.save(p2);
        pedidoRepository.save(p3);

        // Invoca a query customizada de integridade do CaixaService
        long ativos = pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO);

        // Retorna exatamente 2, pois o p3 está FINALIZADO e é ignorado pela query de pendências
        assertThat(ativos).isEqualTo(2L);
    }
}