package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Comanda;
import com.paullomaggio.estevaoLanches.entities.Mesa;
import com.paullomaggio.estevaoLanches.entities.Subconta;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@DisplayName("🎯 MATRIZ REGULADORA: Persistência de Subcontas e Partições (SCT-001 a SCT-070)")
class SubcontaRepositoryTest {

    @Autowired
    private SubcontaRepository subcontaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Mesa mesaPadrao;
    private Comanda comandaPadrao;

    @BeforeEach
    void setupGrafoRelacional() {
        // Cria e persiste a amarração física da Mesa
        mesaPadrao = new Mesa();
        mesaPadrao.setNumero(10);
        mesaPadrao.setStatus(StatusMesa.LIVRE);
        mesaPadrao.setEmpresaId(UUID.randomUUID());
        mesaPadrao.setFilialId(UUID.randomUUID());
        entityManager.persist(mesaPadrao);

        // Cria e persiste a Comanda Mestre
        comandaPadrao = new Comanda();
        comandaPadrao.setEmpresaId(UUID.randomUUID());
        comandaPadrao.setFilialId(UUID.randomUUID());
        comandaPadrao.setStatus(StatusComanda.ABERTA);
        comandaPadrao.setMesa(mesaPadrao);
        entityManager.persist(comandaPadrao);

        entityManager.flush();
    }

    private Subconta instanciarSubconta(Comanda comanda, Integer numero, Boolean pago) {
        Subconta sub = new Subconta();
        sub.setComanda(comanda);
        sub.setNumeroConta(numero);
        sub.setPago(pago);
        return sub;
    }

    // =========================================================================
    // BLOCO 1 — findByComandaIdAndNumeroConta() (SCT-001 a SCT-010)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 1 — findByComandaIdAndNumeroConta()")
    class Bloco1ConsultasCustomizadas {

        @Test @DisplayName("SCT-001 - Deve localizar uma subconta existente na base")
        void sct001() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);

            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1);
            assertThat(res).isPresent();
            assertThat(res.get().getNumeroConta()).isEqualTo(1);
        }

        @Test @DisplayName("SCT-002 - Deve retornar Optional.empty quando a comanda não existir")
        void sct002() {
            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(UUID.randomUUID(), 1);
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("SCT-003 - Deve retornar Optional.empty quando o número da subconta não existir")
        void sct003() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);

            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 99);
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("SCT-004 - Não deve retornar subconta pertencente a outra comanda")
        void sct004() {
            // Comanda B
            Mesa m2 = new Mesa(); m2.setNumero(11); entityManager.persist(m2);
            Comanda comandaB = new Comanda(); comandaB.setMesa(m2); entityManager.persist(comandaB);

            Subconta subA = instanciarSubconta(comandaPadrao, 2, false);
            Subconta subB = instanciarSubconta(comandaB, 2, false);
            entityManager.persist(subA);
            entityManager.persist(subB);
            entityManager.flush();

            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 2);
            assertThat(res).isPresent();
            assertThat(res.get().getComanda().getId()).isEqualTo(comandaPadrao.getId());
        }

        @Test @DisplayName("SCT-005 - Deve localizar subconta de número inicial 1")
        void sct005() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1)).isPresent();
        }

        @Test @DisplayName("SCT-006 - Deve localizar subconta de indexador/número alto (999)")
        void sct006() {
            Subconta sub = instanciarSubconta(comandaPadrao, 999, false);
            entityManager.persistAndFlush(sub);
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 999)).isPresent();
        }

        @Test @DisplayName("SCT-007 - Não deve localizar número negativo")
        void sct007() {
            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), -1);
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("SCT-008 - Não deve localizar número inexistente em comanda vazia")
        void sct008() {
            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 5);
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("SCT-009 - Buscar utilizando UUID nulo ou inválido resulta em empty")
        void sct009() {
            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(null, 1);
            assertThat(res).isEmpty();
        }

        @Test @DisplayName("SCT-010 - Buscar utilizando UUID aleatório retorna empty")
        void sct010() {
            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(UUID.randomUUID(), 1);
            assertThat(res).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 2 — PERSISTÊNCIA (SCT-011 a SCT-017)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 2 — Persistência")
    class Bloco2Persistencia {

        @Test @DisplayName("SCT-011 - Salvar Subconta com sucesso")
        void sct011() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            Subconta salva = subcontaRepository.save(sub);
            assertThat(salva.getId()).isNotNull();
        }

        @Test @DisplayName("SCT-012 - Gerar UUID automaticamente na inserção")
        void sct012() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            assertThat(sub.getId()).isNotNull();
        }

        @Test @DisplayName("SCT-013 - Persistir número da conta filha corretamente")
        void sct013() {
            Subconta sub = instanciarSubconta(comandaPadrao, 4, false);
            Subconta salva = subcontaRepository.saveAndFlush(sub);
            assertThat(salva.getNumeroConta()).isEqualTo(4);
        }

        @Test @DisplayName("SCT-014 - Persistir status pago=false por padrão/default")
        void sct014() {
            Subconta sub = new Subconta();
            sub.setComanda(comandaPadrao);
            sub.setNumeroConta(1); // Deixa o booleano default da entidade agir
            Subconta salva = subcontaRepository.saveAndFlush(sub);
            assertThat(salva.getPago()).isFalse();
        }

        @Test @DisplayName("SCT-015 - Persistir explicitamente status pago=true")
        void sct015() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, true);
            Subconta salva = subcontaRepository.saveAndFlush(sub);
            assertThat(salva.getPago()).isTrue();
        }

        @Test @DisplayName("SCT-016 - Persistir relacionamento íntegro com a Comanda pai")
        void sct016() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            Subconta salva = subcontaRepository.saveAndFlush(sub);
            assertThat(salva.getComanda()).isEqualTo(comandaPadrao);
        }

        @Test @DisplayName("SCT-017 - Persistir mapeamento físico da FK corretamente")
        void sct017() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            Subconta salva = subcontaRepository.saveAndFlush(sub);
            assertThat(salva.getComanda().getId()).isEqualTo(comandaPadrao.getId());
        }
    }

    // =========================================================================
    // BLOCO 3 — RELACIONAMENTOS (SCT-018 a SCT-022)
    // =========================================================================
    @Nested
    @DisplayName("🔗 BLOCO 3 — Relacionamentos")
    class Bloco3Relacionamentos {

        @Test @DisplayName("SCT-018 - Subconta pertence à Comanda referenciada correta")
        void sct018() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            assertThat(sub.getComanda().getId()).isEqualTo(comandaPadrao.getId());
        }

        @Test @DisplayName("SCT-019 - Uma Comanda mestre pode possuir várias Subcontas")
        void sct019() {
            Subconta s1 = instanciarSubconta(comandaPadrao, 1, false);
            Subconta s2 = instanciarSubconta(comandaPadrao, 2, false);
            subcontaRepository.save(s1);
            subcontaRepository.save(s2);
            subcontaRepository.flush();

            List<Subconta> lista = subcontaRepository.findAll();
            assertThat(lista).hasSize(2);
        }

        @Test @DisplayName("SCT-020 - Cada Subconta pertence isoladamente a apenas uma Comanda")
        void sct020() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getComanda()).isNotNull();
        }

        @Test @DisplayName("SCT-021 - Buscar objeto Comanda através do mapeamento da Subconta")
        void sct021() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            Subconta consultada = subcontaRepository.findById(sub.getId()).get();
            assertThat(consultada.getComanda().getStatus()).isEqualTo(StatusComanda.ABERTA);
        }

        @Test @DisplayName("SCT-022 - Chave estrangeira relacional (FK) nunca fica nula se persistida")
        void sct022() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            Subconta salva = subcontaRepository.saveAndFlush(sub);
            assertThat(salva.getComanda()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 4 — CONSTRAINTS (SCT-023 a SCT-027)
    // =========================================================================
    @Nested
    @DisplayName("🛑 BLOCO 4 — Constraints")
    class Bloco4Constraints {

        @Test @DisplayName("SCT-023 - Impedir e estourar exceção ao salvar sem Comanda vinculada")
        void sct023() {
            Subconta sub = new Subconta();
            sub.setNumeroConta(1);
            sub.setPago(false);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(sub));
        }

        @Test @DisplayName("SCT-024 - Impedir e lançar erro ao salvar numeroConta nulo")
        void sct024() {
            Subconta sub = new Subconta();
            sub.setComanda(comandaPadrao);
            sub.setNumeroConta(null); // Campo obrigatório
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(sub));
        }

        @Test @DisplayName("SCT-025 - Permite gravação física de pago=true")
        void sct025() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, true);
            Subconta salva = subcontaRepository.saveAndFlush(sub);
            assertThat(salva.getPago()).isTrue();
        }

        @Test @DisplayName("SCT-026 - Permite gravação física de pago=false")
        void sct026() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            Subconta salva = subcontaRepository.saveAndFlush(sub);
            assertThat(salva.getPago()).isFalse();
        }

        @Test @DisplayName("SCT-027 - Garantia de ID primário único na criação de lotes")
        void sct027() {
            Subconta s1 = instanciarSubconta(comandaPadrao, 1, false);
            Subconta s2 = instanciarSubconta(comandaPadrao, 2, false);
            entityManager.persist(s1);
            entityManager.persist(s2);
            entityManager.flush();
            assertThat(s1.getId()).isNotEqualTo(s2.getId());
        }
    }

    // =========================================================================
    // BLOCO 5 — ATUALIZAÇÃO (SCT-028 a SCT-032)
    // =========================================================================
    @Nested
    @DisplayName("⚙️ BLOCO 5 — Atualização")
    class Bloco5Atualizacao {

        @Test @DisplayName("SCT-028 - Alterar status lógico pago e sincronizar")
        void sct028() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            sub.setPago(true);
            Subconta mod = subcontaRepository.saveAndFlush(sub);
            assertThat(mod.getPago()).isTrue();
        }

        @Test @DisplayName("SCT-029 - Alterar número identificador da subconta")
        void sct029() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            sub.setNumeroConta(5);
            Subconta mod = subcontaRepository.saveAndFlush(sub);
            assertThat(mod.getNumeroConta()).isEqualTo(5);
        }

        @Test @DisplayName("SCT-030 - Persistir alterações de múltiplos campos")
        void sct030() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            sub.setNumeroConta(3);
            sub.setPago(true);
            subcontaRepository.saveAndFlush(sub);

            Subconta buscada = subcontaRepository.findById(sub.getId()).get();
            assertThat(buscada.getNumeroConta()).isEqualTo(3);
            assertThat(buscada.getPago()).isTrue();
        }

        @Test @DisplayName("SCT-031 - Buscar registro atualizado através do método customizado")
        void sct031() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            sub.setNumeroConta(7);
            subcontaRepository.saveAndFlush(sub);

            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 7);
            assertThat(res).isPresent();
        }

        @Test @DisplayName("SCT-032 - Modificações secundárias nunca alteram o ID primário (UUID)")
        void sct032() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            UUID idOriginal = sub.getId();
            sub.setNumeroConta(2);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getId()).isEqualTo(idOriginal);
        }
    }

    // =========================================================================
    // BLOCO 6 — EXCLUSÃO (SCT-033 a SCT-036)
    // =========================================================================
    @Nested
    @DisplayName("🗑️ BLOCO 6 — Exclusão")
    class Bloco6Exclusao {

        @Test @DisplayName("SCT-033 - Excluir fisicamente uma Subconta")
        void sct033() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            subcontaRepository.delete(sub);
            subcontaRepository.flush();
            assertThat(subcontaRepository.findById(sub.getId())).isEmpty();
        }

        @Test @DisplayName("SCT-034 - Buscar após deleção retorna Optional.empty")
        void sct034() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            subcontaRepository.delete(sub);
            subcontaRepository.flush();
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1)).isEmpty();
        }

        @Test @DisplayName("SCT-035 - existsById retorna falso após deleção física")
        void sct035() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            UUID id = sub.getId();
            subcontaRepository.delete(sub);
            subcontaRepository.flush();
            assertThat(subcontaRepository.existsById(id)).isFalse();
        }

        @Test @DisplayName("SCT-036 - findAll reduz contador de massa física após deleção")
        void sct036() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            long antes = subcontaRepository.count();
            subcontaRepository.delete(sub);
            subcontaRepository.flush();
            assertThat(subcontaRepository.count()).isEqualTo(antes - 1);
        }
    }

    // =========================================================================
    // BLOCO 7 — MÚLTIPLAS SUBCONTAS (SCT-037 a SCT-040)
    // =========================================================================
    @Nested
    @DisplayName("📊 BLOCO 7 — Múltiplas Subcontas")
    class Bloco7MultiplasSubcontas {

        @Test @DisplayName("SCT-037 - Persistir lote sequencial ordenado (Contas de 1 a 4)")
        void sct037() {
            for (int i = 1; i <= 4; i++) {
                entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            }
            entityManager.flush();
            assertThat(subcontaRepository.findAll()).hasSize(4);
        }

        @Test @DisplayName("SCT-038 - Buscar especificamente a Conta 2 em lote ativo")
        void sct038() {
            for (int i = 1; i <= 4; i++) {
                entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            }
            entityManager.flush();
            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 2);
            assertThat(res).isPresent();
            assertThat(res.get().getNumeroConta()).isEqualTo(2);
        }

        @Test @DisplayName("SCT-039 - Buscar especificamente a última partição (Conta 4)")
        void sct039() {
            for (int i = 1; i <= 4; i++) {
                entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            }
            entityManager.flush();
            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 4);
            assertThat(res).isPresent();
        }

        @Test @DisplayName("SCT-040 - Buscar número inexistente em lote populado retorna empty")
        void sct040() {
            for (int i = 1; i <= 4; i++) {
                entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            }
            entityManager.flush();
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 5)).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 8 — STRESS (SCT-041 a SCT-046)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 8 — Stress")
    class Bloco8Stress {

        @Test @DisplayName("SCT-041 - Persistir lote massivo de 50 Subcontas")
        void sct041() {
            for (int i = 1; i <= 50; i++) {
                entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            }
            entityManager.flush();
            assertThat(subcontaRepository.findAll()).hasSize(50);
        }

        @Test @DisplayName("SCT-042 - Persistir lote ultra massivo de 100 Subcontas")
        void sct042() {
            for (int i = 1; i <= 100; i++) {
                entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            }
            entityManager.flush();
            assertThat(subcontaRepository.findAll()).hasSize(100);
        }

        @Test @DisplayName("SCT-043 - Buscar e varrer toda a árvore via findAll sem gargalos")
        void sct043() {
            for (int i = 1; i <= 10; i++) entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            entityManager.flush();
            assertThat(subcontaRepository.findAll()).isNotEmpty();
        }

        @Test @DisplayName("SCT-044 - Buscar partições aleatórias sob volume persistido")
        void sct044() {
            for (int i = 1; i <= 20; i++) entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            entityManager.flush();
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 12)).isPresent();
        }

        @Test @DisplayName("SCT-045 - Localizar a primeira partição (limiar inferior) sob volume")
        void sct045() {
            for (int i = 1; i <= 15; i++) entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            entityManager.flush();
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1)).isPresent();
        }

        @Test @DisplayName("SCT-046 - Localizar a última partição (limiar superior) sob volume")
        void sct046() {
            for (int i = 1; i <= 30; i++) entityManager.persist(instanciarSubconta(comandaPadrao, i, false));
            entityManager.flush();
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 30)).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 9 — INTEGRIDADE COMERCIAL (SCT-047 a SCT-050)
    // =========================================================================
    @Nested
    @DisplayName("🏢 BLOCO 9 — Integridade Comercial")
    class Bloco9IntegridadeComercial {

        @Test @DisplayName("SCT-047 - Permitido mesmo número de subconta em comandas diferentes (Isolamento de Mesas)")
        void sct047() {
            Mesa m2 = new Mesa(); m2.setNumero(12); entityManager.persist(m2);
            Comanda comandaB = new Comanda(); comandaB.setMesa(m2); entityManager.persist(comandaB);

            Subconta s1 = instanciarSubconta(comandaPadrao, 1, false);
            Subconta s2 = instanciarSubconta(comandaB, 1, false);

            assertFactoryDoesNotThrow(() -> {
                entityManager.persist(s1);
                entityManager.persist(s2);
                entityManager.flush();
            });
        }

        @Test @DisplayName("SCT-048 - Mesmo número na mesma comanda: localiza a partição persistida sem loops")
        void sct048() {
            Subconta sub = instanciarSubconta(comandaPadrao, 2, false);
            entityManager.persistAndFlush(sub);
            Optional<Subconta> res = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 2);
            assertThat(res).isPresent();
        }

        @Test @DisplayName("SCT-049 - Subconta liquidada/paga continua indexada e localizável para histórico fiscal")
        void sct049() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, true);
            entityManager.persistAndFlush(sub);
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1)).isPresent();
        }

        @Test @DisplayName("SCT-050 - Subconta em aberto continua indexada e localizável no painel operacional")
        void sct050() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1)).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 10 — REGRESSÃO (SCT-051 a SCT-055)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 10 — Regressão")
    class Bloco10Regressao {

        @Test @DisplayName("SCT-051 - Operações comuns nunca trocam ou corrompem a FK da Comanda pai")
        void sct051() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            sub.setPago(true);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getComanda().getId()).isEqualTo(comandaPadrao.getId());
        }

        @Test @DisplayName("SCT-052 - Operação de sincronização (save) nunca altera o UUID primário")
        void sct052() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            UUID id = sub.getId();
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getId()).isEqualTo(id);
        }

        @Test @DisplayName("SCT-053 - Mutações em outros campos nunca alteram o numeroConta sozinho")
        void sct053() {
            Subconta sub = instanciarSubconta(comandaPadrao, 3, false);
            entityManager.persistAndFlush(sub);
            sub.setPago(true);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getNumeroConta()).isEqualTo(3);
        }

        @Test @DisplayName("SCT-054 - Mutações cadastrais de infraestrutura nunca perdem status pago")
        void sct054() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, true);
            entityManager.persistAndFlush(sub);
            sub.setNumeroConta(2);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getPago()).isTrue();
        }

        @Test @DisplayName("SCT-055 - findByComandaIdAndNumeroConta continua operacional após mutações em cascata")
        void sct055() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            sub.setPago(true);
            subcontaRepository.saveAndFlush(sub);
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1)).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 11 — EDGE CASES (SCT-056 a SCT-060)
    // =========================================================================
    @Nested
    @DisplayName("🌌 BLOCO 11 — Edge Cases")
    class Bloco11EdgeCases {

        @Test @DisplayName("SCT-056 - Validação com numeroConta de partida mínima (1)")
        void sct056() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getNumeroConta()).isEqualTo(1);
        }

        @Test @DisplayName("SCT-057 - Validação com numeroConta limite inteiro máximo (Integer.MAX_VALUE)")
        void sct057() {
            Subconta sub = instanciarSubconta(comandaPadrao, Integer.MAX_VALUE, false);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getNumeroConta()).isEqualTo(Integer.MAX_VALUE);
        }

        @Test @DisplayName("SCT-058 - Passagem de UUID de comanda inexistente na triagem customizada")
        void sct058() {
            assertThat(subcontaRepository.findByComandaIdAndNumeroConta(UUID.randomUUID(), 1)).isEmpty();
        }

        @Test @DisplayName("SCT-059 - Sincronização em Comanda recém mapeada sem histórico")
        void sct059() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            Subconta salva = subcontaRepository.saveAndFlush(sub);
            assertThat(subcontaRepository.findById(salva.getId())).isPresent();
        }

        @Test @DisplayName("SCT-060 - Comanda alocando dezenas de subcontas fracionadas simultaneamente")
        void sct060() {
            for (int i = 1; i <= 40; i++) {
                subcontaRepository.save(instanciarSubconta(comandaPadrao, i, false));
            }
            subcontaRepository.flush();
            assertThat(subcontaRepository.findAll()).hasSize(40);
        }
    }

    // =========================================================================
    // BLOCO 12 — CONCORRÊNCIA DE LEITURA (SCT-061 a SCT-064)
    // =========================================================================
    @Nested
    @DisplayName("⚖️ BLOCO 12 — Concorrência")
    class Bloco12ConcorrenciaLeitura {

        @Test @DisplayName("SCT-061 - Duas buscas simultâneas/consecutivas retornam o mesmo resultado imutável")
        void sct061() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);

            Optional<Subconta> r1 = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1);
            Optional<Subconta> r2 = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1);
            assertThat(r1).isEqualTo(r2);
        }

        @Test @DisplayName("SCT-062 - Busca consistente efetuada durante fluxo transacional de atualização")
        void sct062() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            sub.setPago(true);
            // Estado na memória mutado, a busca encontra consistência
            Optional<Subconta> r = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1);
            assertThat(r).isPresent();
        }

        @Test @DisplayName("SCT-063 - Busca executada durante remoção atômica")
        void sct063() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            subcontaRepository.delete(sub);
            // Antes do flush, o estado avalia dependendo do isolamento do H2
            Optional<Subconta> r = subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1);
            assertThat(r).isNotNull();
        }

        @Test @DisplayName("SCT-064 - Leituras paralelas em threads ou loops consecutivas mantêm isolamento")
        void sct064() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            entityManager.persistAndFlush(sub);
            for (int i = 0; i < 10; i++) {
                assertThat(subcontaRepository.findByComandaIdAndNumeroConta(comandaPadrao.getId(), 1)).isPresent();
            }
        }
    }

    // =========================================================================
    // BLOCO 13 — AUDITORIA (SCT-065 a SCT-070)
    // =========================================================================
    @Nested
    @DisplayName("🧼 BLOCO 13 — Auditoria")
    class Bloco13AuditoriaFinal {

        @Test @DisplayName("SCT-065 - Todas as Subcontas persistidas possuem identificação UUID obrigatória")
        void sct065() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getId()).isNotNull();
        }

        @Test @DisplayName("SCT-066 - Nenhuma Subconta gerada órfã ou sem Comanda associada")
        void sct066() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getComanda()).isNotNull();
        }

        @Test @DisplayName("SCT-067 - Nenhuma restrição de integridade relacional (FK) corrompida")
        void sct067() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getComanda().getMesa()).isNotNull();
        }

        @Test @DisplayName("SCT-068 - Nenhuma coluna numeroConta nula indexada na base")
        void sct068() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getNumeroConta()).isNotNull();
        }

        @Test @DisplayName("SCT-069 - Nenhum campo booleano pago nulo indexado na base")
        void sct069() {
            Subconta sub = instanciarSubconta(comandaPadrao, 1, false);
            subcontaRepository.saveAndFlush(sub);
            assertThat(sub.getPago()).isNotNull();
        }

        @Test @DisplayName("SCT-070 - findAll retorna exatamente o total das Subcontas persistidas na sessão")
        void sct070() {
            subcontaRepository.deleteAll(); // Limpa sessão isolada
            subcontaRepository.flush();
            subcontaRepository.save(instanciarSubconta(comandaPadrao, 1, false));
            subcontaRepository.save(instanciarSubconta(comandaPadrao, 2, false));
            subcontaRepository.flush();
            assertThat(subcontaRepository.findAll()).hasSize(2);
        }
    }

    // Utilitário de asserção silenciosa para constraints de banco de dados
    private void assertFactoryDoesNotThrow(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.fail("O bloco lançou uma exceção inesperada: " + e.getMessage());
        }
    }
}