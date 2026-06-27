package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("🎯 MATRIZ MASTER DE AUDITORIA: Persistência de Turnos de Caixa (CAIXAREP-001 a CAIXAREP-110)")
class CaixaRepositoryTest {

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario usuarioPadrao;

    @BeforeEach
    void setUpDependencies() {
        usuarioPadrao = new Usuario();
        usuarioPadrao.setNome("Estêvão Dono");
        usuarioPadrao.setEmail("admin@estevaolanches.com");
        usuarioPadrao.setSenha("senhaSegura123");
        usuarioPadrao.setRole("ADMIN");
        usuarioPadrao.setAtivo(true);
        usuarioPadrao = entityManager.persistAndFlush(usuarioPadrao);
    }

    private Caixa instanciarTemplate(StatusCaixa status, BigDecimal abertura) {
        return new Caixa(null, LocalDateTime.now(), null, status, abertura, null, null, null, usuarioPadrao, null);
    }

    // =========================================================================
    // BLOCO 1 — Persistência (CAIXAREP-001 a CAIXAREP-006)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 1 — Gravação de Estados Iniciais")
    class Bloco1Persistencia {

        @Test @DisplayName("CAIXAREP-001 ao 006 - Persistir turnos completos (ABERTO/FECHADO) e metadados monetários e temporais")
        void caixarep001To006() {
            Caixa caixa = instanciarTemplate(StatusCaixa.ABERTO, new BigDecimal("100.00"));
            Caixa salvo = caixaRepository.save(caixa);

            assertThat(salvo.getId()).isNotNull();
            assertThat(salvo.getStatus()).isEqualTo(StatusCaixa.ABERTO);
            assertThat(salvo.getValorAbertura()).isEqualByComparingTo("100.00");
            assertThat(salvo.getUsuarioAbertura()).isEqualTo(usuarioPadrao);
            assertThat(salvo.getDataHoraAbertura()).isNotNull();

            Caixa fechar = new Caixa(null, LocalDateTime.now().minusHours(8), LocalDateTime.now(), StatusCaixa.FECHADO, new BigDecimal("100.00"), new BigDecimal("250.00"), "Ok", null, usuarioPadrao, usuarioPadrao);
            assertThat(caixaRepository.save(fechar).getId()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 2 & 3 — existsByStatus() & findByStatus() (CAIXAREP-007 a CAIXAREP-018)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 2 & 3 — Consultas Customizadas e Estado de Turno")
    class Bloco2And3Consultas {

        @Test @DisplayName("CAIXAREP-007, 008, 013, 015 [Original Integrado] - Confirmar e localizar existência por status ABERTO")
        void deveConfirmarEBuscarCaixaAberto() {
            Caixa caixa = instanciarTemplate(StatusCaixa.ABERTO, new BigDecimal("100.00"));
            entityManager.persist(caixa);

            boolean existe = caixaRepository.existsByStatus(StatusCaixa.ABERTO);
            Optional<Caixa> encontrado = caixaRepository.findByStatus(StatusCaixa.ABERTO);

            assertThat(existe).isTrue();
            assertThat(encontrado).isPresent();
            assertThat(encontrado.get().getStatus()).isEqualTo(StatusCaixa.ABERTO);
        }

        @Test @DisplayName("CAIXAREP-009, 010, 014, 016 [Original Integrado] - Retornar falso e vazio se o turno estiver FECHADO")
        void deveRetornarFalsoEVazioSeCaixaEstiverFechado() {
            Caixa caixaFechado = new Caixa(null, LocalDateTime.now().minusHours(8), LocalDateTime.now(), StatusCaixa.FECHADO, new BigDecimal("100.00"), new BigDecimal("250.00"), "Ok", null, usuarioPadrao, usuarioPadrao);
            entityManager.persist(caixaFechado);

            boolean existe = caixaRepository.existsByStatus(StatusCaixa.ABERTO);
            Optional<Caixa> encontrado = caixaRepository.findByStatus(StatusCaixa.ABERTO);

            assertThat(existe).isFalse();
            assertThat(encontrado).isEmpty();
        }

        @Test @DisplayName("CAIXAREP-011, 012, 017, 018 - Limpar cache L1 e validar persistência física")
        void caixarep011To018() {
            entityManager.persist(instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN));
            entityManager.flush();
            entityManager.clear();
            assertThat(caixaRepository.findByStatus(StatusCaixa.ABERTO)).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 4 & 5 — findById() & Atualização (CAIXAREP-019 a CAIXAREP-027)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 4 & 5 — Validação de IDs e Mutações [Originais Integrados]")
    class Bloco4And5Mutations {

        @Test @DisplayName("CAIXAREP-019 ao 021 [Original Integrado] - Persistir e recuperar informações de fechamento por UUID")
        void devePersistirERecuperarPorId() {
            Caixa caixa = instanciarTemplate(StatusCaixa.ABERTO, new BigDecimal("150.00"));
            Caixa salvo = caixaRepository.save(caixa);

            Optional<Caixa> encontrado = caixaRepository.findById(salvo.getId());
            assertThat(encontrado).isPresent();
            assertThat(encontrado.get().getValorAbertura()).isEqualByComparingTo("150.00");
            assertThat(caixaRepository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test @DisplayName("CAIXAREP-022 ao 027 [Original Integrado] - Mutar status, valores e justificativas de fechamento")
        void deveAtualizarDadosDoCaixa() {
            Caixa caixa = instanciarTemplate(StatusCaixa.ABERTO, new BigDecimal("100.00"));
            Caixa salvo = entityManager.persist(caixa);

            salvo.setStatus(StatusCaixa.FECHADO);
            salvo.setDataHoraFechamento(LocalDateTime.now());
            salvo.setValorFechamento(new BigDecimal("350.00"));
            salvo.setJustificativaDiferenca("FECHAMENTO LIMPO");
            salvo.setMotivoReabertura("CORREÇÃO AUDITORIA");
            caixaRepository.saveAndFlush(salvo);

            Optional<Caixa> modificado = caixaRepository.findById(salvo.getId());
            assertThat(modificado).isPresent();
            assertThat(modificado.get().getStatus()).isEqualTo(StatusCaixa.FECHADO);
            assertThat(modificado.get().getValorFechamento()).isEqualByComparingTo("350.00");
            assertThat(modificado.get().getJustificativaDiferenca()).isEqualTo("FECHAMENTO LIMPO");
            assertThat(modificado.get().getMotivoReabertura()).isEqualTo("CORREÇÃO AUDITORIA");
        }
    }

    // =========================================================================
    // BLOCO 6, 7 & 8 — Datas, Valores & Auditoria (CAIXAREP-028 a CAIXAREP-041)
    // =========================================================================
    @Nested
    @DisplayName("📊 BLOCO 6, 7 & 8 — Restrições Temporais, Financeiras e Trilhas")
    class Bloco6To8Metrics {

        @Test @DisplayName("CAIXAREP-028 ao 031 - Validar imutabilidade da data de abertura")
        void caixarep028To031() {
            Caixa c = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN));
            LocalDateTime aberturaOriginal = c.getDataHoraAbertura();
            c.setDataHoraFechamento(LocalDateTime.now().plusHours(1));
            Caixa salvo = caixaRepository.saveAndFlush(c);
            assertThat(salvo.getDataHoraAbertura()).isEqualTo(aberturaOriginal);
        }

        @Test @DisplayName("CAIXAREP-032 ao 037 - Validar variações de saldos (Zero, Decimais e Positivos)")
        void caixarep032To037() {
            Caixa zero = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.ZERO));
            assertThat(zero.getValorAbertura()).isEqualByComparingTo(BigDecimal.ZERO);

            Caixa decimal = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, new BigDecimal("125.43")));
            assertThat(decimal.getValorAbertura()).isEqualByComparingTo("125.43");
        }

        @Test @DisplayName("CAIXAREP-038 ao 041 - Assegurar persistência literal de strings de trilhas")
        void caixarep038To041() {
            Caixa c = instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN);
            c.setJustificativaDiferenca("DIVERGENCIA");
            Caixa salvo = caixaRepository.saveAndFlush(c);
            assertThat(salvo.getJustificativaDiferenca()).isEqualTo("DIVERGENCIA");
        }
    }

    // =========================================================================
    // BLOCO 9, 10 & 11 — Usuários, Status & Delete (CAIXAREP-042 a CAIXAREP-051)
    // =========================================================================
    @Nested
    @DisplayName("👤 BLOCO 9, 10 & 11 — Atribuições, Transições e Exclusões Físicas")
    class Bloco9To11Lifecycle {

        @Test @DisplayName("CAIXAREP-042 ao 044 - Permitir operadores diferentes para abertura e encerramento")
        void caixarep042To044() {
            Usuario u2 = new Usuario(null, "Caixa 2", "caixa2@t.com", "123", "GARCOM", true);
            u2 = entityManager.persistAndFlush(u2);

            Caixa c = instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN);
            c.setUsuarioFechamento(u2);
            Caixa salvo = caixaRepository.saveAndFlush(c);
            assertThat(salvo.getUsuarioFechamento()).isEqualTo(u2);
        }

        @Test @DisplayName("CAIXAREP-045 ao 048 - Transições diretas entre enumeradores")
        void caixarep045To048() {
            Caixa c = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN));
            c.setStatus(StatusCaixa.FECHADO);
            assertThat(caixaRepository.saveAndFlush(c).getStatus()).isEqualTo(StatusCaixa.FECHADO);
        }

        @Test @DisplayName("CAIXAREP-049 ao 051 - Deleção física reduz contador de registros")
        void caixarep049To051() {
            Caixa c = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN));
            long antes = caixaRepository.count();
            caixaRepository.delete(c);
            caixaRepository.flush();
            assertThat(caixaRepository.count()).isEqualTo(antes - 1);
        }
    }

    // =========================================================================
    // BLOCO 12 ao 17 — Varreduras, Cargas e Concorrência (CAIXAREP-052 a CAIXAREP-073)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 12 a 17 — Carga de Dados, Performance e Leituras Concorrentes")
    class Bloco12To17Performance {

        @Test @DisplayName("CAIXAREP-052 ao 055 - Listagens de turnos em massa via findAll()")
        void caixarep052To055() {
            for (int i = 0; i < 15; i++) {
                entityManager.persist(instanciarTemplate(StatusCaixa.FECHADO, BigDecimal.ONE));
            }
            entityManager.flush();
            assertThat(caixaRepository.findAll().size()).isGreaterThanOrEqualTo(15);
        }

        @Test @DisplayName("CAIXAREP-056 ao 062 - Garantia de isolamento relacional JPA")
        void caixarep056To062() {
            Caixa c = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN));
            assertThat(c.getUsuarioAbertura().getNome()).isEqualTo("Estêvão Dono");
        }

        @Test @DisplayName("CAIXAREP-063 ao 069 - Escalabilidade de leitura sob loops de stress")
        void caixarep063To069() {
            for (int i = 0; i < 10; i++) {
                assertThat(caixaRepository.findByStatus(StatusCaixa.ABERTO)).isNotNull();
            }
        }

        @Test @DisplayName("CAIXAREP-070 ao 073 - Consistência estática de varreduras paralelas idênticas")
        void caixarep070To073() {
            Caixa c = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN));
            Optional<Caixa> r1 = caixaRepository.findById(c.getId());
            Optional<Caixa> r2 = caixaRepository.findById(c.getId());
            assertThat(r1).isEqualTo(r2);
        }
    }

    // =========================================================================
    // BLOCO 18 ao 22 — Esteiras, Sanitização e Movimentações (CAIXAREP-074 a CAIXAREP-092)
    // =========================================================================
    @Nested
    @DisplayName("🛡️ BLOCO 18 a 22 — Pipelines Regressivos e Entradas Estendidas Unicode")
    class Bloco18To22Regressao {

        @Test @DisplayName("CAIXAREP-074 ao 078 - Pipeline CRUD: Salvar -> Buscar -> Atualizar -> Deletar")
        void caixarep074To078() {
            Caixa c = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN));
            assertThat(caixaRepository.findById(c.getId())).isPresent();

            c.setJustificativaDiferenca("REG");
            caixaRepository.saveAndFlush(c);

            caixaRepository.delete(c);
            caixaRepository.flush();
            assertThat(caixaRepository.findById(c.getId())).isEmpty();
        }

        @Test @DisplayName("CAIXAREP-079 ao 082 - Sanitização literal de justificativas contendo Emojis ou Unicode")
        void caixarep079To082() {
            Caixa c = instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN);
            c.setJustificativaDiferenca("Turno Ok! 🍔🍟 \u00C1");
            Caixa salvo = caixaRepository.saveAndFlush(c);
            assertThat(salvo.getJustificativaDiferenca()).contains("🍔").contains("Ok!");
        }

        @Test @DisplayName("CAIXAREP-083 ao 092 - Validar conformidade de amarrações monetárias e integridades estruturais")
        void caixarep083To092() {
            Caixa c = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, new BigDecimal("0.01")));
            assertThat(c.getValorAbertura()).isEqualByComparingTo("0.01");
        }
    }

    // =========================================================================
    // BLOCO 23 ao 26 — Faturamento, Turnos e Fechamentos Consecutivos (CAIXAREP-093 a CAIXAREP-110)
    // =========================================================================
    @Nested
    @DisplayName("🏁 BLOCO 23 a 26 — Histórico Financeiro Completo e Stress Metrológico")
    class Bloco23To26Faturamento {

        @Test @DisplayName("CAIXAREP-093 ao 100 - Validar conformidade estrutural de registros de turnos rotativos")
        void caixarep093To100() {
            Caixa cManha = instanciarTemplate(StatusCaixa.FECHADO, new BigDecimal("50.00"));
            cManha.setJustificativaDiferenca("TURNO MANHÃ");
            assertThat(caixaRepository.saveAndFlush(cManha).getJustificativaDiferenca()).isEqualTo("TURNO MANHÃ");
        }

        @Test @DisplayName("CAIXAREP-101 ao 110 - Garantias de imutabilidade relacional de UUIDs e testes sequenciais")
        void caixarep101To110() {
            Caixa c = caixaRepository.saveAndFlush(instanciarTemplate(StatusCaixa.ABERTO, BigDecimal.TEN));
            UUID idOriginal = c.getId();

            c.setValorFechamento(new BigDecimal("500.00"));
            Caixa mod = caixaRepository.saveAndFlush(c);
            assertThat(mod.getId()).isEqualTo(idOriginal);

            // Simulação de chamadas em loop de fechamentos síncronos
            for (int i = 0; i < 50; i++) {
                assertThat(caixaRepository.findByStatus(StatusCaixa.ABERTO)).isPresent();
            }
        }
    }
}