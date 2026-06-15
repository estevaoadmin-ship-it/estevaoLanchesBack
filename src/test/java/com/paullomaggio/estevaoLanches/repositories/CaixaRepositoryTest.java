package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.enums.RoleUsuario;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CaixaRepositoryTest {

    @Autowired
    private CaixaRepository cajaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario gerente;
    private Usuario operador;

    @BeforeEach
    void setUp() {
        gerente = new Usuario(null, "Estêvão Dono", "admin@estevaolanches.com", "123", RoleUsuario.ADMIN, true);
        operador = new Usuario(null, "João Caixa", "caixa@estevaolanches.com", "123", RoleUsuario.GARCOM, true);

        entityManager.persist(gerente);
        entityManager.persist(operador);
        entityManager.flush();
    }

    @Test
    @DisplayName("CT-001 - Deve retornar true quando existir caixa ABERTO")
    void existsByStatusCenario1() {
        Caixa caixa = new Caixa(null, LocalDateTime.now(), null, StatusCaixa.ABERTO, BigDecimal.TEN, null, gerente, null);
        entityManager.persist(caixa);

        boolean resultado = cajaRepository.existsByStatus(StatusCaixa.ABERTO);

        assertTrue(resultado);
    }

    @Test
    @DisplayName("CT-002 - Deve retornar false quando não existir caixa ABERTO")
    void existsByStatusCenario2() {
        boolean resultado = cajaRepository.existsByStatus(StatusCaixa.ABERTO);

        assertFalse(resultado);
    }

    @Test
    @DisplayName("CT-003 - Deve retornar true quando existir caixa FECHADO")
    void existsByStatusCenario3() {
        Caixa caixa = new Caixa(null, LocalDateTime.now().minusDays(1), LocalDateTime.now(), StatusCaixa.FECHADO, BigDecimal.TEN, BigDecimal.valueOf(150), gerente, operador);
        entityManager.persist(caixa);

        boolean resultado = cajaRepository.existsByStatus(StatusCaixa.FECHADO);

        assertTrue(resultado);
    }

    @Test
    @DisplayName("CT-004 - Deve localizar caixa aberto")
    void findByStatusCenario1() {
        Caixa caixa = new Caixa(null, LocalDateTime.now(), null, StatusCaixa.ABERTO, BigDecimal.TEN, null, gerente, null);
        entityManager.persist(caixa);

        Optional<Caixa> resultado = cajaRepository.findByStatus(StatusCaixa.ABERTO);

        assertTrue(resultado.isPresent());
        assertEquals(StatusCaixa.ABERTO, resultado.get().getStatus());
    }

    @Test
    @DisplayName("CT-005 - Deve localizar caixa fechado")
    void findByStatusCenario2() {
        Caixa caixa = new Caixa(null, LocalDateTime.now().minusDays(1), LocalDateTime.now(), StatusCaixa.FECHADO, BigDecimal.TEN, BigDecimal.valueOf(200), gerente, gerente);
        entityManager.persist(caixa);

        Optional<Caixa> resultado = cajaRepository.findByStatus(StatusCaixa.FECHADO);

        assertTrue(resultado.isPresent());
        assertEquals(StatusCaixa.FECHADO, resultado.get().getStatus());
    }

    @Test
    @DisplayName("CT-006 - Deve retornar Optional vazio quando não existir")
    void findByStatusCenario3() {
        Optional<Caixa> resultado = cajaRepository.findByStatus(StatusCaixa.ABERTO);

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("CT-007 - Deve retornar o caixa correto quando houver registros mistos")
    void findByStatusCenario4() {
        Caixa caixaFechado = new Caixa(null, LocalDateTime.now().minusDays(1), LocalDateTime.now(), StatusCaixa.FECHADO, BigDecimal.TEN, BigDecimal.TEN, gerente, gerente);
        Caixa caixaAberto = new Caixa(null, LocalDateTime.now(), null, StatusCaixa.ABERTO, BigDecimal.valueOf(50), null, gerente, null);

        entityManager.persist(caixaFechado);
        entityManager.persist(caixaAberto);

        Optional<Caixa> resultado = cajaRepository.findByStatus(StatusCaixa.ABERTO);

        assertTrue(resultado.isPresent());
        assertEquals(0, BigDecimal.valueOf(50).compareTo(resultado.get().getValorAbertura()));
    }

    @Test
    @DisplayName("CT-008 - Deve persistir caixa corretamente")
    void persistenciaCenario1() {
        Caixa caixa = new Caixa(null, LocalDateTime.now(), null, StatusCaixa.ABERTO, BigDecimal.TEN, null, gerente, null);

        Caixa salvo = cajaRepository.save(caixa);

        assertNotNull(salvo.getId());
        assertTrue(cajaRepository.findById(salvo.getId()).isPresent());
    }

    @Test
    @DisplayName("CT-009 - Deve persistir relacionamentos de usuário")
    void persistenciaCenario2() {
        Caixa caixa = new Caixa(null, LocalDateTime.now(), null, StatusCaixa.ABERTO, BigDecimal.TEN, null, gerente, null);
        Caixa salvo = cajaRepository.save(caixa);

        entityManager.flush();
        entityManager.clear();

        Optional<Caixa> buscadoOpt = cajaRepository.findById(salvo.getId());
        assertTrue(buscadoOpt.isPresent());

        Caixa buscado = buscadoOpt.get();
        assertNotNull(buscado.getUsuarioAbertura());
        assertEquals("Estêvão Dono", buscado.getUsuarioAbertura().getNome());
    }
}