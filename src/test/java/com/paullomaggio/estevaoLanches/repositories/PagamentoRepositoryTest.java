package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Pagamento;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PagamentoRepositoryTest {

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("🎯 FIX STRING FIELD: Injeta o nome do Enum como String para bater com a propriedade da entidade")
    void deveSomarPagamentosDaContaEspecifica() {
        UUID pedidoId = UUID.randomUUID();

        Pagamento p1 = new Pagamento();
        ReflectionTestUtils.setField(p1, "pedidoId", pedidoId);
        ReflectionTestUtils.setField(p1, "numeroConta", 1);
        // 🎯 FIX: Convertido o Enum para String (.name()) eliminando a colisão de tipos no reflexão
        ReflectionTestUtils.setField(p1, "formaPagamento", FormaPagamento.PIX.name());
        ReflectionTestUtils.setField(p1, "valorPago", new BigDecimal("15.50"));

        Pagamento p2 = new Pagamento();
        ReflectionTestUtils.setField(p2, "pedidoId", pedidoId);
        ReflectionTestUtils.setField(p2, "numeroConta", 1);
        ReflectionTestUtils.setField(p2, "formaPagamento", FormaPagamento.PIX.name());
        ReflectionTestUtils.setField(p2, "valorPago", new BigDecimal("20.00"));

        entityManager.persist(p1);
        entityManager.persist(p2);
        entityManager.flush();

        BigDecimal totalConta1 = pagamentoRepository.sumPagamentosPorConta(pedidoId, 1);
        assertThat(totalConta1).isEqualByComparingTo(new BigDecimal("35.50"));
    }
}