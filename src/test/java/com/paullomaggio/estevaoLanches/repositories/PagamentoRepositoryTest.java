package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("🧪 Testes de Repositório — PagamentoRepository")
class PagamentoRepositoryTest {

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Deve somar todos os pagamentos vinculados fisicamente ao ID de uma Conta mestre")
    void deveSomarPagamentosDaContaEspecifica() {
        // 1. Setup de IDs e massas de dados compartilhadas da Empresa/Filial
        UUID empresaId = UUID.randomUUID();
        UUID filialId = UUID.randomUUID();

        // 2. Persiste a Mesa obrigatória
        Mesa mesa = new Mesa();
        mesa.setNumero(42);
        mesa.setStatus(StatusMesa.OCUPADA);
        mesa.setEmpresaId(empresaId);
        mesa.setFilialId(filialId);
        mesa = entityManager.persist(mesa);

        // 3. Persiste a Comanda Mestre
        Comanda comanda = new Comanda();
        comanda.setMesa(mesa);
        comanda.setStatus(StatusComanda.ABERTA);
        comanda.setAbertaEm(LocalDateTime.now());
        comanda.setEmpresaId(empresaId);
        comanda.setFilialId(filialId);
        comanda = entityManager.persist(comanda);

        // 4. Persiste o Cliente dono da cadeira
        Cliente cliente = new Cliente();
        cliente.setNome("PEDRO SILVA");
        cliente.setStatus(StatusCliente.ATIVO);
        cliente = entityManager.persist(cliente);

        // 5. Persiste a Conta (Subcomanda) vinculada à sessão da mesa
        Conta conta = new Conta();
        conta.setNumeroConta(1);
        conta.setPago(false);
        conta.setValorTotal(new BigDecimal("50.00"));
        conta.setComanda(comanda);
        conta.setCliente(cliente);
        conta = entityManager.persist(conta);

        // 6. Instancia o Primeiro Pagamento Parcial (R$ 15.50)
        Pagamento p1 = new Pagamento();
        p1.setConta(conta); // Ancoragem física ajustada
        p1.setFormaPagamento(FormaPagamento.PIX); // Tipado diretamente pelo Enum reaproveitado
        p1.setValorPago(new BigDecimal("15.50"));
        p1.setDataHora(LocalDateTime.now());
        p1.setUsuarioResponsavel("CAIXA_TESTE");

        // 7. Instancia o Segundo Pagamento Parcial (R$ 20.00)
        Pagamento p2 = new Pagamento();
        p2.setConta(conta);
        p2.setFormaPagamento(FormaPagamento.PIX);
        p2.setValorPago(new BigDecimal("20.00"));
        p2.setDataHora(LocalDateTime.now());
        p2.setUsuarioResponsavel("CAIXA_TESTE");

        entityManager.persist(p1);
        entityManager.persist(p2);

        entityManager.flush();
        entityManager.clear();

        // 8. Execução da consulta JPQL reestruturada focada no ID da Conta
        BigDecimal totalConta1 = pagamentoRepository.sumPagamentosPorConta(conta.getId());

        // 9. Asserção matemática de consistência (15.50 + 20.00 = 35.50)
        assertThat(totalConta1).isEqualByComparingTo(new BigDecimal("35.50"));
    }
}