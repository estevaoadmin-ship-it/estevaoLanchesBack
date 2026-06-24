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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("🧪 Testes de Repositório — CaixaRepository")
class CaixaRepositoryTest {

    @Autowired private CaixaRepository caixaRepository;
    @Autowired private TestEntityManager entityManager;

    private Usuario usuarioPadrao;

    @BeforeEach
    void setUp() {
        // 🎯 FIX HELPER DEFINITIVO: Objeto construído com TODAS as propriedades obrigatórias da entidade
        usuarioPadrao = new Usuario();
        usuarioPadrao.setNome("Estêvão Dono");
        usuarioPadrao.setEmail("admin@estevaolanches.com");
        usuarioPadrao.setSenha("senhaSegura123"); // 🛡️ Evita erro: NULL not allowed for column "SENHA"
        usuarioPadrao.setRole(RoleUsuario.ADMIN);
        usuarioPadrao.setAtivo(true);

        usuarioPadrao = entityManager.persist(usuarioPadrao);
    }

    @Test
    @DisplayName("Deve confirmar existência de caixa quando status for ABERTO")
    void deveConfirmarExistenciaDeCaixaAberto() {
        Caixa caixa = new Caixa(null, LocalDateTime.now(), null, StatusCaixa.ABERTO, new BigDecimal("100.00"), null, null, null, usuarioPadrao, null);
        entityManager.persist(caixa);

        boolean existe = caixaRepository.existsByStatus(StatusCaixa.ABERTO);
        assertThat(existe).isTrue();
    }

    @Test
    @DisplayName("Deve retornar falso para caixa aberto se o turno estiver FECHADO")
    void deveRetornarFalsoSeCaixaEstiverFechado() {
        Caixa caixaFechado = new Caixa(null, LocalDateTime.now().minusHours(8), LocalDateTime.now(), StatusCaixa.FECHADO, new BigDecimal("100.00"), new BigDecimal("250.00"), "Ok", null, usuarioPadrao, usuarioPadrao);
        entityManager.persist(caixaFechado);

        boolean existe = caixaRepository.existsByStatus(StatusCaixa.ABERTO);
        assertThat(existe).isFalse();
    }

    @Test
    @DisplayName("Deve localizar o caixa ativo por status com sucesso")
    void deveBuscarCaixaAtivoPorStatus() {
        Caixa caixa = new Caixa(null, LocalDateTime.now(), null, StatusCaixa.ABERTO, new BigDecimal("100.00"), null, null, null, usuarioPadrao, null);
        entityManager.persist(caixa);

        Optional<Caixa> encontrado = caixaRepository.findByStatus(StatusCaixa.ABERTO);
        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getStatus()).isEqualTo(StatusCaixa.ABERTO);
    }

    @Test
    @DisplayName("Deve retornar vazio ao buscar caixa ativo se todos estiverem fechados")
    void deveRetornarVazioSeNaoHouverCaixaAtivo() {
        Caixa caixaFechado = new Caixa(null, LocalDateTime.now().minusHours(5), LocalDateTime.now(), StatusCaixa.FECHADO, new BigDecimal("50.00"), new BigDecimal("50.00"), "Ok", null, usuarioPadrao, usuarioPadrao);
        entityManager.persist(caixaFechado);

        Optional<Caixa> encontrado = caixaRepository.findByStatus(StatusCaixa.ABERTO);
        assertThat(encontrado).isEmpty();
    }
}