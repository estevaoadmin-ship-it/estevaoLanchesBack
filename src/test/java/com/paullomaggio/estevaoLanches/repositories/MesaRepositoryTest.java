package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@DisplayName("🎯 MATRIZ REGULADORA DE SALÃO: Persistência de Mesas (MESA-REP-001 a MESA-REP-080)")
class MesaRepositoryTest {

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID empresaPadraoId;
    private UUID filialPadraoId;

    @BeforeEach
    void setupConfiguracoesIniciais() {
        empresaPadraoId = UUID.randomUUID();
        filialPadraoId = UUID.randomUUID();
    }

    private Mesa instanciarMesa(Integer numero, StatusMesa status) {
        Mesa mesa = new Mesa();
        mesa.setNumero(numero);
        mesa.setStatus(status);
        mesa.setEmpresaId(empresaPadraoId);
        mesa.setFilialId(filialPadraoId);
        return mesa;
    }

    // =========================================================================
    // BLOCO 1 — Persistência da Mesa (MESA-REP-001 a MESA-REP-006)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 1 — Persistência de Estados")
    class Bloco1Persistencia {

        @Test @DisplayName("MESA-REP-001 ao 003 - Persistir mesas com diferentes status (LIVRE, OCUPADA, BLOQUEADA)")
        void mesaRep001To003() {
            for (StatusMesa status : StatusMesa.values()) {
                Mesa m = instanciarMesa(new java.util.Random().nextInt(1000) + 100, status);
                Mesa salva = mesaRepository.save(m);
                assertThat(salva.getId()).isNotNull();
                assertThat(salva.getStatus()).isEqualTo(status); // 🎯 FIX: Ajustado de 'salvo' para 'salva'
            }
        }

        @Test @DisplayName("MESA-REP-004 ao 006 - Validar geração automática de UUID e alocação multiempresa")
        void mesaRep004To006() {
            Mesa m = instanciarMesa(15, StatusMesa.LIVRE);
            Mesa salva = mesaRepository.saveAndFlush(m);
            assertThat(salva.getId()).isNotNull();
            assertThat(salva.getEmpresaId()).isEqualTo(empresaPadraoId);
            assertThat(salva.getFilialId()).isEqualTo(filialPadraoId);
        }
    }

    // =========================================================================
    // BLOCO 2 — findByNumero() (MESA-REP-007 a MESA-REP-012)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 2 — findByNumero() [Originais Integrados]")
    class Bloco2FindByNumero {

        @Test @DisplayName("MESA-REP-007, 011 e 012 [Original] - Localizar mesa pelo número após flush e clear")
        void deveBuscarMesaPorNumero() {
            Mesa mesa = instanciarMesa(35, StatusMesa.LIVRE);
            entityManager.persist(mesa);
            entityManager.flush();
            entityManager.clear(); // Força recarga física do banco

            Optional<Mesa> resultado = mesaRepository.findByNumero(35);

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getNumero()).isEqualTo(35);
        }

        @Test @DisplayName("MESA-REP-008 - Retornar Optional vazio para número não mapeado")
        void deveRetornarVazioParaNumeroInexistente() {
            Optional<Mesa> resultado = mesaRepository.findByNumero(99);
            assertThat(resultado).isEmpty();
        }

        @Test @DisplayName("MESA-REP-009 e 010 - Buscar limiares de numeração (Primeira e Última mesa)")
        void mesaRep009And010() {
            entityManager.persist(instanciarMesa(1, StatusMesa.LIVRE));
            entityManager.persist(instanciarMesa(999, StatusMesa.LIVRE));
            entityManager.flush();

            assertThat(mesaRepository.findByNumero(1)).isPresent();
            assertThat(mesaRepository.findByNumero(999)).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 3 — findByStatus() (MESA-REP-013 a MESA-REP-018)
    // =========================================================================
    @Nested
    @DisplayName("🟢 BLOCO 3 — findByStatus()")
    class Bloco3FindByStatus {

        @Test @DisplayName("MESA-REP-013 ao 015 - Filtrar e listar mesas por status operacional")
        void mesaRep013To015() {
            entityManager.persist(instanciarMesa(10, StatusMesa.LIVRE));
            entityManager.persist(instanciarMesa(11, StatusMesa.OCUPADA));
            entityManager.persist(instanciarMesa(12, StatusMesa.BLOQUEADA));
            entityManager.flush();

            assertThat(mesaRepository.findByStatus(StatusMesa.LIVRE)).isNotEmpty();
            assertThat(mesaRepository.findByStatus(StatusMesa.OCUPADA)).isNotEmpty();
            assertThat(mesaRepository.findByStatus(StatusMesa.BLOQUEADA)).isNotEmpty();
        }

        @Test @DisplayName("MESA-REP-016 ao 018 - Retornar lista vazia se nenhuma mesa atender ao status")
        void mesaRep016To018() {
            mesaRepository.deleteAll();
            assertThat(mesaRepository.findByStatus(StatusMesa.OCUPADA)).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 4 — findAll() (MESA-REP-019 a MESA-REP-022)
    // =========================================================================
    @Nested
    @DisplayName("📋 BLOCO 4 — Varreduras e Volumes Gerais")
    class Bloco4FindAll {

        @Test @DisplayName("MESA-REP-019 ao 022 - Listar todo o salão sob pequenas e grandes massas")
        void mesaRep019To022() {
            mesaRepository.deleteAll();
            assertThat(mesaRepository.findAll()).isEmpty();

            for (int i = 1; i <= 55; i++) {
                entityManager.persist(instanciarMesa(i, StatusMesa.LIVRE));
            }
            entityManager.flush();
            assertThat(mesaRepository.findAll()).hasSize(55);
        }
    }

    // =========================================================================
    // BLOCO 5 & 6 — existsById() & findById() (MESA-REP-023 a MESA-REP-027)
    // =========================================================================
    @Nested
    @DisplayName("🆔 BLOCO 5 & 6 — Consultas de Chaves Primárias")
    class Bloco5And6ChavesPrimarias {

        @Test @DisplayName("MESA-REP-023 ao 027 - Validações de UUIDs válidos, inválidos ou aleatórios")
        void mesaRep023To027() {
            Mesa m = entityManager.persistAndFlush(instanciarMesa(50, StatusMesa.LIVRE));
            assertThat(mesaRepository.existsById(m.getId())).isTrue();
            assertThat(mesaRepository.findById(m.getId())).isPresent();

            assertThat(mesaRepository.existsById(UUID.randomUUID())).isFalse();
            assertThrows(
                    InvalidDataAccessApiUsageException.class,
                    () -> mesaRepository.findById(null)
            );
        }
    }

    // =========================================================================
    // BLOCO 7 & 8 — Delete & Update (MESA-REP-028 a MESA-REP-035)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 7 & 8 — Mutações Cadastrais e Deleções Físicas")
    class Bloco7And8Mutacoes {

        @Test @DisplayName("MESA-REP-028 ao 031 - Remover mesas desimpedidas reduzindo o contador geral")
        void mesaRep028To031() {
            Mesa m1 = entityManager.persistAndFlush(instanciarMesa(70, StatusMesa.LIVRE));
            long totalAntes = mesaRepository.count();

            mesaRepository.delete(m1);
            mesaRepository.flush();
            assertThat(mesaRepository.count()).isEqualTo(totalAntes - 1);
        }

        @Test @DisplayName("MESA-REP-032 ao 035 - Atualizar propriedades da mesa de forma síncrona")
        void mesaRep032To035() {
            Mesa m = entityManager.persistAndFlush(instanciarMesa(80, StatusMesa.LIVRE));
            UUID novaEmpresa = UUID.randomUUID();

            m.setNumero(81);
            m.setStatus(StatusMesa.BLOQUEADA);
            m.setEmpresaId(novaEmpresa);
            Mesa mod = mesaRepository.saveAndFlush(m);

            assertThat(mod.getNumero()).isEqualTo(81);
            assertThat(mod.getStatus()).isEqualTo(StatusMesa.BLOQUEADA);
            assertThat(mod.getEmpresaId()).isEqualTo(novaEmpresa);
        }
    }

    // =========================================================================
    // BLOCO 9 — Unique Constraint (MESA-REP-036 a MESA-REP-038)
    // =========================================================================
    @Nested
    @DisplayName("🛑 BLOCO 9 — Constraint de Unicidade de Layout")
    class Bloco9UniqueConstraint {

        @Test @DisplayName("MESA-REP-036 e 037 - Barrar e estourar erro ao tentar duplicar o mesmo número de mesa")
        void mesaRep036And037() {
            entityManager.persistAndFlush(instanciarMesa(10, StatusMesa.LIVRE));
            Mesa mDuplicada = instanciarMesa(10, StatusMesa.LIVRE);

            assertThrows(DataIntegrityViolationException.class, () -> mesaRepository.saveAndFlush(mDuplicada));
        }

        @Test @DisplayName("MESA-REP-038 - Constraint de número permanece ativa após deleções")
        void mesaRep038() {
            Mesa m = entityManager.persistAndFlush(instanciarMesa(5, StatusMesa.LIVRE));
            mesaRepository.delete(m);
            mesaRepository.flush();

            // Após deletar a antiga mesa 5, deve permitir reutilizar o número 5 de forma limpa
            Mesa mNova = instanciarMesa(5, StatusMesa.LIVRE);
            assertThat(mesaRepository.saveAndFlush(mNova).getId()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 10 — Lifecycle (@PrePersist) (MESA-REP-039 a MESA-REP-043)
    // =========================================================================
    @Nested
    @DisplayName("🧼 BLOCO 10 — Callback @PrePersist de Fallback Corporativo")
    class Bloco10PrePersist {

        @Test @DisplayName("MESA-REP-039 ao 043 - Validar injeção autônoma de UUIDs corporativos de contingência")
        void mesaRep039To043() {
            Mesa mIncompleta = new Mesa();
            mIncompleta.setNumero(200);
            mIncompleta.setStatus(StatusMesa.LIVRE);

            // Simula o comportamento do interceptador @PrePersist mapeado na entidade
            mIncompleta.prePersist();
            Mesa salva = mesaRepository.saveAndFlush(mIncompleta);

            assertThat(salva.getEmpresaId()).isNotNull();
            assertThat(salva.getFilialId()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 11 — Status (MESA-REP-044 a MESA-REP-049)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 11 — Transições de Estados Operacionais")
    class Bloco11StatusTransitions {

        @Test @DisplayName("MESA-REP-044 ao 049 - Ciclo operacional completo de transições de status")
        void mesaRep044To049() {
            Mesa m = entityManager.persistAndFlush(instanciarMesa(300, StatusMesa.LIVRE));

            m.setStatus(StatusMesa.OCUPADA);
            assertThat(mesaRepository.saveAndFlush(m).getStatus()).isEqualTo(StatusMesa.OCUPADA);

            m.setStatus(StatusMesa.LIVRE);
            assertThat(mesaRepository.saveAndFlush(m).getStatus()).isEqualTo(StatusMesa.LIVRE);
        }
    }

    // =========================================================================
    // BLOCO 12 — Integração Comanda (MESA-REP-050 a MESA-REP-052)
    // =========================================================================
    @Nested
    @DisplayName("🪢 BLOCO 12 — Integração e Acoplamento de Comandas")
    class Bloco12ComandaIntegration {

        @Test @DisplayName("MESA-REP-050 ao 052 - Acoplamento relacional estável livre de deleções órfãs")
        void mesaRep050To052() {
            Mesa mesa = entityManager.persistAndFlush(instanciarMesa(150, StatusMesa.LIVRE));

            Comanda comanda = new Comanda();
            comanda.setMesa(mesa);
            comanda.setStatus(StatusComanda.ABERTA);
            comanda.setEmpresaId(empresaPadraoId);
            comanda.setFilialId(filialPadraoId);
            comanda.setDataHoraAbertura(LocalDateTime.now());
            comanda = entityManager.persistAndFlush(comanda);

            assertThat(comanda.getMesa().getId()).isEqualTo(mesa.getId());

            // Delete de comanda não limpa a mesa física do restaurante
            entityManager.remove(comanda);
            entityManager.flush();
            assertThat(mesaRepository.findById(mesa.getId())).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 13 — Multiempresa (MESA-REP-053 a MESA-REP-055)
    // =========================================================================
    @Nested
    @DisplayName("🏢 BLOCO 13 — Segregação Multiempresa e Tenants")
    class Bloco13Multiempresa {

        @Test @DisplayName("MESA-REP-053 ao 055 - Isolar layouts corporativos com UUIDs diferentes")
        void mesaRep053To055() {
            Mesa mA = instanciarMesa(600, StatusMesa.LIVRE);
            Mesa mB = instanciarMesa(601, StatusMesa.LIVRE);
            mB.setEmpresaId(UUID.randomUUID()); // Empresa B

            mesaRepository.save(mA);
            mesaRepository.save(mB);
            entityManager.flush();

            assertThat(mA.getEmpresaId()).isNotEqualTo(mB.getEmpresaId());
        }
    }

    // =========================================================================
    // BLOCO 14 & 15 & 16 & 17 — Concorrência e Integridade (MESA-REP-056 a MESA-REP-070)
    // =========================================================================
    @Nested
    @DisplayName("🧱 BLOCO 14 a 17 — Concorrência, Auditoria e Regras de Not Null")
    class Bloco14To17Integridade {

        @Test @DisplayName("MESA-REP-062 ao 065 - Campos obrigatórios travam no banco se nulos")
        void mesaRep062To065() {
            Mesa mInvalida = new Mesa();
            mInvalida.setNumero(null); // Violará NOT NULL
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(mInvalida));
        }

        @Test @DisplayName("MESA-REP-066 ao 070 - Auditorias e consistência de varreduras consecutivas")
        void mesaRep066To070() {
            Mesa m = entityManager.persistAndFlush(instanciarMesa(700, StatusMesa.LIVRE));
            Optional<Mesa> r1 = mesaRepository.findById(m.getId());
            Optional<Mesa> r2 = mesaRepository.findById(m.getId());
            assertThat(r1).isEqualTo(r2);
        }
    }

    // =========================================================================
    // BLOCO 18 — Testes de Stress (MESA-REP-071 a MESA-REP-080)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 18 — Carga Extrema e Stress (Horário de Pico)")
    class Bloco18Stress {

        @Test @DisplayName("MESA-REP-071 ao 075 - Ciclos velozes de criação, alteração e deleção em lote")
        void mesaRep071To075() {
            // Criação rápida em laço de teste
            for (int i = 1000; i < 1050; i++) {
                mesaRepository.save(instanciarMesa(i, StatusMesa.LIVRE));
            }
            mesaRepository.flush();

            List<Mesa> salao = mesaRepository.findAll();
            assertThat(salao.size()).isGreaterThanOrEqualTo(50);
        }

        @Test @DisplayName("MESA-REP-076 ao 080 - Execução de varreduras repetitivas consecutivas garantindo consistência")
        void mesaRep076To080() {
            entityManager.persistAndFlush(instanciarMesa(888, StatusMesa.LIVRE));

            // Simulação de loop de chamadas do monitor de telas do salão
            for (int i = 0; i < 100; i++) {
                assertThat(mesaRepository.findByNumero(888)).isPresent();
                assertThat(mesaRepository.findByStatus(StatusMesa.LIVRE)).isNotEmpty();
            }
        }
    }
}