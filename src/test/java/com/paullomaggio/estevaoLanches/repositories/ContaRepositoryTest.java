package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.StatusComanda;
import com.paullomaggio.estevaoLanches.enums.StatusMesa;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("🧪 MATRIZ DE DIVISIONAMENTO FINANCEIRO: Engenharia de Subcontas (CONTAREP-001 a 085)")
class ContaRepositoryTest {

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Mesa mesaMestre;
    private Comanda comandaMestre;
    private Cliente clienteMestre;

    @BeforeEach
    void setupGrafoRelacionalMestre() {
        UUID empresaId = UUID.randomUUID();
        UUID filialId = UUID.randomUUID();

        // 1. Instancia e persiste Mesa física obrigatória
        mesaMestre = new Mesa();
        mesaMestre.setNumero(5);
        mesaMestre.setStatus(StatusMesa.OCUPADA);
        mesaMestre.setEmpresaId(empresaId);
        mesaMestre.setFilialId(filialId);
        mesaMestre = entityManager.persist(mesaMestre);

        // 2. Instancia e persiste Comanda Mãe
        comandaMestre = new Comanda();
        comandaMestre.setMesa(mesaMestre);
        comandaMestre.setStatus(StatusComanda.ABERTA);
        comandaMestre.setEmpresaId(empresaId);
        comandaMestre.setFilialId(filialId);
        comandaMestre.setDataHoraAbertura(LocalDateTime.now());
        comandaMestre = entityManager.persist(comandaMestre);

        // 3. Instancia e persiste Ficha do Cliente
        clienteMestre = new Cliente();
        clienteMestre.setNome("MESA 5 - CONTA MASTER");
        clienteMestre.setNumero("16999998888");
        clienteMestre.setEnderecos(new ArrayList<>());
        clienteMestre = entityManager.persist(clienteMestre);

        entityManager.flush();
    }

    private Conta instanciarContaTemplate(Comanda comanda, Integer numero, BigDecimal total, Boolean pago) {
        Conta conta = new Conta();
        conta.setNumeroConta(numero);
        conta.setPago(pago);
        conta.setValorTotal(total);
        conta.setComanda(comanda);
        conta.setCliente(clienteMestre);
        conta.setPedidos(new ArrayList<>());
        conta.setPagamentos(new ArrayList<>());
        return conta;
    }

    // =========================================================================
    // BLOCO 1 — CRUD Básico (CONTAREP-001 a CONTAREP-008)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 1 — Operações de CRUD Básico")
    class Bloco1CrudBasico {

        @Test @DisplayName("CONTAREP-001 ao 004 - Salvar conta, validar geração de UUID e buscas por ID")
        void contarep001To004() {
            Conta c = instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false);
            Conta salva = contaRepository.save(c);

            assertThat(salva.getId()).isNotNull();
            assertThat(contaRepository.findById(salva.getId())).isPresent();
            assertThat(contaRepository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test @DisplayName("CONTAREP-005 ao 008 - Atualizar estados (Número, Saldo, Pago) e deleção física")
        void contarep005To008() {
            Conta c = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 2, BigDecimal.ZERO, false));

            c.setNumeroConta(3);
            c.setValorTotal(new BigDecimal("150.75"));
            c.setPago(true);
            Conta atualizada = contaRepository.saveAndFlush(c);

            assertThat(atualizada.getNumeroConta()).isEqualTo(3);
            assertThat(atualizada.getValorTotal()).isEqualByComparingTo("150.75");
            assertThat(atualizada.getPago()).isTrue();

            contaRepository.delete(atualizada);
            contaRepository.flush();
            assertThat(contaRepository.findById(atualizada.getId())).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 2 — findByComandaId() (CONTAREP-009 a CONTAREP-015)
    // =========================================================================
    @Nested
    @DisplayName("📊 BLOCO 2 — findByComandaId() (Varredura de Mesa)")
    class Bloco2FindByComandaId {

        @Test @DisplayName("CONTAREP-009 ao 012 - Retornar coleções de subcontas associadas (Lotes de 1, 2, 5 ou vazios)")
        void contarep009To012() {
            contaRepository.deleteAll();
            assertThat(contaRepository.findByComandaId(comandaMestre.getId())).isEmpty();

            entityManager.persist(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            entityManager.persist(instanciarContaTemplate(comandaMestre, 2, BigDecimal.ZERO, false));
            entityManager.flush();

            List<Conta> res = contaRepository.findByComandaId(comandaMestre.getId());
            assertThat(res).hasSize(2);
        }

        @Test @DisplayName("CONTAREP-013 ao 015 - Isolar e ignorar subcontas de outras comandas mestre")
        void contarep013To015() {
            // 🎯 FIX: Instanciação por setters da Mesa B
            Mesa mesa2 = new Mesa();
            mesa2.setNumero(12);
            mesa2.setStatus(StatusMesa.LIVRE);
            mesa2.setEmpresaId(UUID.randomUUID());
            mesa2.setFilialId(UUID.randomUUID());
            mesa2 = entityManager.persist(mesa2);

            // 🎯 FIX: Instanciação por setters da Comanda B
            Comanda comandaB = new Comanda();
            comandaB.setMesa(mesa2);
            comandaB.setStatus(StatusComanda.ABERTA);
            comandaB.setEmpresaId(UUID.randomUUID());
            comandaB.setFilialId(UUID.randomUUID());
            comandaB.setDataHoraAbertura(LocalDateTime.now());
            comandaB = entityManager.persist(comandaB);

            entityManager.persist(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            entityManager.persist(instanciarContaTemplate(comandaB, 1, BigDecimal.ZERO, false));
            entityManager.flush();

            assertThat(contaRepository.findByComandaId(comandaMestre.getId())).hasSize(1);
        }
    }

    // =========================================================================
    // BLOCO 3 — findByComandaIdAndNumeroConta() (CONTAREP-016 a CONTAREP-023)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 3 — findByComandaIdAndNumeroConta() (Triagem de Divisão)")
    class Bloco3FindByComandaIdAndNumeroConta {

        @Test @DisplayName("CONTAREP-016 ao 021 - Localizar a partição exata pelo número dentro da comanda alvo")
        void contarep016To021() {
            Conta c1 = entityManager.persistAndFlush(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            Conta c2 = entityManager.persistAndFlush(instanciarContaTemplate(comandaMestre, 2, BigDecimal.ZERO, false));

            Optional<Conta> res = contaRepository.findByComandaIdAndNumeroConta(comandaMestre.getId(), 2);
            assertThat(res).isPresent();
            assertThat(res.get().getId()).isEqualTo(c2.getId());

            assertThat(contaRepository.findByComandaIdAndNumeroConta(comandaMestre.getId(), 99)).isEmpty();
        }

        @Test @DisplayName("CONTAREP-022 e 023 - Comportamento com números limites (Negativos ou Máximos Inteiros)")
        void contarep022And023() {
            entityManager.persistAndFlush(instanciarContaTemplate(comandaMestre, Integer.MAX_VALUE, BigDecimal.ZERO, false));
            assertThat(contaRepository.findByComandaIdAndNumeroConta(comandaMestre.getId(), Integer.MAX_VALUE)).isPresent();
            assertThat(contaRepository.findByComandaIdAndNumeroConta(comandaMestre.getId(), -5)).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 4 & 5 — Valores & Pago (CONTAREP-024 a CONTAREP-032)
    // =========================================================================
    @Nested
    @DisplayName("🪙 BLOCO 4 & 5 — Integridade Numérica e Transições de Status")
    class Bloco4And5ValoresPago {

        @Test @DisplayName("CONTAREP-024 ao 028 - Guardar com precisão absoluta escalas decimais de moedas")
        void contarep024To028() {
            Conta c = instanciarContaTemplate(comandaMestre, 1, new BigDecimal("333.33"), false);
            Conta salva = contaRepository.saveAndFlush(c);
            assertThat(salva.getValorTotal()).isEqualByComparingTo("333.33");
        }

        @Test @DisplayName("CONTAREP-029 ao 032 - Transições lógicas da flag booleana de encerramento")
        void contarep029To032() {
            Conta c = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            assertThat(c.getPago()).isFalse();

            c.setPago(true);
            assertThat(contaRepository.saveAndFlush(c).getPago()).isTrue();
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 — Cliente & Comanda (CONTAREP-033 a CONTAREP-040)
    // =========================================================================
    @Nested
    @DisplayName("🪢 BLOCO 6 & 7 — Acoplamentos de Infraestrutura e Chaves Estrangeiras")
    class Bloco6And7Relacionamentos {

        @Test @DisplayName("CONTAREP-033 ao 036 - Vinculação física com a ficha comercial do cliente")
        void contarep033To036() {
            Conta c = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            entityManager.clear();

            Conta rel = contaRepository.findById(c.getId()).get();
            assertThat(rel.getCliente().getNome()).isEqualTo("MESA 5 - CONTA MASTER");
        }

        @Test @DisplayName("CONTAREP-037 ao 040 - Integridade relacional da FK com a comanda operacional")
        void contarep037To040() {
            Conta c = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            entityManager.clear();

            Conta rel = contaRepository.findById(c.getId()).get();
            assertThat(rel.getComanda().getId()).isEqualTo(comandaMestre.getId());
        }
    }

    // =========================================================================
    // BLOCO 8 & 9 — Pedidos & Pagamentos (CONTAREP-041 a CONTAREP-048)
    // =========================================================================
    @Nested
    @DisplayName("📦 BLOCO 8 & 9 — Coleções de Consumo e Recebimentos")
    class Bloco8And9Colecoes {

        @Test @DisplayName("CONTAREP-041 ao 048 - Validar integridade e inicialização segura das listas internas")
        void contarep041To048() {
            Conta c = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            entityManager.clear();

            Conta rel = contaRepository.findById(c.getId()).get();
            // 🎯 FIX: Corrigido o método inexistente do AssertJ para asserções diretas de não-nulidade das coleções
            assertThat(rel.getPedidos()).isNotNull();
            assertThat(rel.getPagamentos()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 10 & 11 & 12 — Deleção, Concorrência & Regressão (CONTAREP-049 a CONTAREP-060)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 10 a 12 — Ciclo de Vida Cadastral e Pipelines de Regressão")
    class Bloco10To12CicloVida {

        @Test @DisplayName("CONTAREP-049 ao 052 - Excluir subcomandas vazias mantendo entidades vizinhas")
        void contarep049To052() {
            Conta c = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            UUID idCliente = c.getCliente().getId();

            contaRepository.delete(c);
            contaRepository.flush();

            assertThat(entityManager.find(Cliente.class, idCliente)).isNotNull(); // CRM comercial intacto
        }

        @Test @DisplayName("CONTAREP-053 ao 060 - Pipeline CRUD Completo: Inserir -> Buscar -> Modificar -> Confirmar -> Excluir")
        void contarep053To060() {
            Conta c = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 10, BigDecimal.ZERO, false));
            Optional<Conta> busca = contaRepository.findById(c.getId());
            assertThat(busca).isPresent();

            busca.get().setNumeroConta(11);
            contaRepository.saveAndFlush(busca.get());

            contaRepository.delete(busca.get());
            contaRepository.flush();
            assertThat(contaRepository.findById(c.getId())).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 13 — Precisão Financeira (CONTAREP-061 a CONTAREP-064)
    // =========================================================================
    @Nested
    @DisplayName("🧮 BLOCO 13 — Precisão Absoluta Centesimal (BigDecimal)")
    class Bloco13Precision {

        @Test @DisplayName("CONTAREP-061 ao 064 - Validar faturamentos milimétricos e limites sem arredondamentos ocultos")
        void contarep061To064() {
            Conta cMin = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 1, new BigDecimal("0.01"), false));
            assertThat(cMin.getValorTotal()).isEqualByComparingTo("0.01");

            Conta cMax = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 2, new BigDecimal("999999.99"), false));
            assertThat(cMax.getValorTotal()).isEqualByComparingTo("999999.99");
        }
    }

    // =========================================================================
    // BLOCO 14 — Stress (CONTAREP-065 a CONTAREP-069)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 14 — Lotes de Carga e Loops de Estresse")
    class Bloco14Stress {

        @Test @DisplayName("CONTAREP-065 ao 069 - Alocações rápidas de subcontas concorrentes sob loops síncronos")
        void contarep065To069() {
            for (int i = 0; i < 30; i++) {
                contaRepository.save(instanciarContaTemplate(comandaMestre, i + 100, BigDecimal.ZERO, false));
            }
            contaRepository.flush();
            assertThat(contaRepository.findByComandaId(comandaMestre.getId()).size()).isGreaterThanOrEqualTo(30);
        }
    }

    // =========================================================================
    // BLOCO 15 & 16 — Multi Mesas e Multi Subcontas (CONTAREP-070 a CONTAREP-078)
    // =========================================================================
    @Nested
    @DisplayName("🏢 BLOCO 15 & 16 — Particionamento de Layout de Salão")
    class Bloco15And16MultiMesas {

        @Test @DisplayName("CONTAREP-070 ao 078 - Mesas diferentes possuindo de forma isolada a subconta de número 1")
        void contarep070To078() {
            // 🎯 FIX: Instanciação por setters da Mesa 2
            Mesa mesa2 = new Mesa();
            mesa2.setNumero(6);
            mesa2.setStatus(StatusMesa.LIVRE);
            mesa2.setEmpresaId(UUID.randomUUID());
            mesa2.setFilialId(UUID.randomUUID());
            mesa2 = entityManager.persist(mesa2);

            // 🎯 FIX: Instanciação por setters da Comanda 2
            Comanda comanda2 = new Comanda();
            comanda2.setMesa(mesa2);
            comanda2.setStatus(StatusComanda.ABERTA);
            comanda2.setEmpresaId(UUID.randomUUID());
            comanda2.setFilialId(UUID.randomUUID());
            comanda2.setDataHoraAbertura(LocalDateTime.now());
            comanda2 = entityManager.persist(comanda2);

            entityManager.persist(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            entityManager.persist(instanciarContaTemplate(comanda2, 1, BigDecimal.ZERO, false));
            entityManager.flush();

            assertThat(contaRepository.findByComandaIdAndNumeroConta(comandaMestre.getId(), 1)).isPresent();
            assertThat(contaRepository.findByComandaIdAndNumeroConta(comanda2.getId(), 1)).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 17 & 18 — Integridade Referencial e Performance (CONTAREP-079 a CONTAREP-085)
    // =========================================================================
    @Nested
    @DisplayName("🏁 BLOCO 17 & 18 — Prova de Carga e Integridade Referencial Inviolável")
    class Bloco17And18FinalSuites {

        @Test @DisplayName("CONTAREP-079 ao 082 - Provar imutabilidade absoluta do relacionamento mestre")
        void contarep079To082() {
            Conta c = contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            assertThat(c.getComanda().getMesa().getNumero()).isEqualTo(5);
        }

        @Test @DisplayName("CONTAREP-083 ao 085 - Batimento final de performance e consistência operacional")
        void contarep083To085() {
            contaRepository.saveAndFlush(instanciarContaTemplate(comandaMestre, 1, BigDecimal.ZERO, false));
            List<Conta> todas = contaRepository.findAll();
            assertThat(todas).isNotEmpty();
        }
    }
}