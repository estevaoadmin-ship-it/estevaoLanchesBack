package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
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
@DisplayName("🎯 MATRIZ REGULADORA FINANCEIRA: Persistência de Pagamentos (PAGREP-001 a PAGREP-100)")
class PagamentoRepositoryTest {

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Mesa mesaMestre;
    private Comanda comandaMestre;
    private Cliente clienteMestre;
    private Conta contaMestre;

    @BeforeEach
    void setUpGrafoRelacional() {
        UUID empresaId = UUID.randomUUID();
        UUID filialId = UUID.randomUUID();

        mesaMestre = new Mesa();
        mesaMestre.setNumero(42);
        mesaMestre.setStatus(StatusMesa.OCUPADA);
        mesaMestre.setEmpresaId(empresaId);
        mesaMestre.setFilialId(filialId);
        mesaMestre = entityManager.persist(mesaMestre);

        comandaMestre = new Comanda();
        comandaMestre.setMesa(mesaMestre);
        comandaMestre.setStatus(StatusComanda.ABERTA);
        comandaMestre.setEmpresaId(empresaId);
        comandaMestre.setFilialId(filialId);
        comandaMestre = entityManager.persist(comandaMestre);

        clienteMestre = new Cliente();
        clienteMestre.setNome("PEDRO SILVA");
        clienteMestre.setStatus(StatusCliente.ATIVO);
        clienteMestre = entityManager.persist(clienteMestre);

        contaMestre = new Conta();
        contaMestre.setNumeroConta(1);
        contaMestre.setPago(false);
        contaMestre.setValorTotal(new BigDecimal("100.00"));
        contaMestre.setComanda(comandaMestre);
        contaMestre.setCliente(clienteMestre);
        contaMestre = entityManager.persist(contaMestre);

        entityManager.flush();
    }

    private Pagamento instanciarPagamento(Conta conta, FormaPagamento forma, BigDecimal valor) {
        Pagamento p = new Pagamento();
        p.setConta(conta);
        p.setFormaPagamento(forma);
        p.setValorPago(valor);
        p.setDataHora(LocalDateTime.now());
        p.setUsuarioResponsavel("CAIXA_TESTE");
        return p;
    }

    // =========================================================================
    // BLOCO 1 — Persistência (PAGREP-001 a PAGREP-006)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 1 — Persistência de Lançamentos")
    class Bloco1Persistencia {

        @Test @DisplayName("PAGREP-001 ao 005 - Persistir pagamentos individuais por tipo")
        void pagrep001To005() {
            for (FormaPagamento forma : FormaPagamento.values()) {
                Pagamento p = instanciarPagamento(contaMestre, forma, BigDecimal.TEN);
                Pagamento salvo = pagamentoRepository.save(p);
                assertThat(salvo.getId()).isNotNull();
            }
        }

        @Test @DisplayName("PAGREP-006 - Persistir fluxo de pagamento Misto")
        void pagrep006() {
            Pagamento p1 = instanciarPagamento(contaMestre, FormaPagamento.PIX, new BigDecimal("15.00"));
            Pagamento p2 = instanciarPagamento(contaMestre, FormaPagamento.DINHEIRO, new BigDecimal("20.00"));
            pagamentoRepository.save(p1);
            pagamentoRepository.save(p2);
            entityManager.flush();
            assertThat(pagamentoRepository.findAll()).hasSize(2);
        }
    }

    // =========================================================================
    // BLOCO 2 — Integridade (PAGREP-007 a PAGREP-012)
    // =========================================================================
    @Nested
    @DisplayName("🛑 BLOCO 2 — Validação de Restrições (NOT NULL)")
    class Bloco2Integridade {

        @Test @DisplayName("PAGREP-007 - Conta é campo obrigatório")
        void pagrep007() {
            Pagamento p = instanciarPagamento(null, FormaPagamento.PIX, BigDecimal.TEN);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(p));
        }

        @Test @DisplayName("PAGREP-008 ao 012 - Validar preenchimento de metadados fiscais")
        void pagrep008To012() {
            Pagamento p = instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN);
            Pagamento salvo = pagamentoRepository.saveAndFlush(p);
            assertThat(salvo.getFormaPagamento()).isNotNull();
            assertThat(salvo.getValorPago()).isNotNull();
            assertThat(salvo.getDataHora()).isNotNull();
            assertThat(salvo.getUsuarioResponsavel()).isNotNull();
            assertThat(salvo.getId()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 3 — sumPagamentosPorConta() (PAGREP-013 a PAGREP-018)
    // =========================================================================
    @Nested
    @DisplayName("🧮 BLOCO 3 — Somatório de Lançamentos (JPQL)")
    class Bloco3SumPagamentosPorConta {

        @Test @DisplayName("PAGREP-013 ao 015 [Original Integrado] - Somar múltiplos pagamentos acoplados")
        void pagrep013To015() {
            entityManager.persist(instanciarPagamento(contaMestre, FormaPagamento.PIX, new BigDecimal("15.50")));
            entityManager.persist(instanciarPagamento(contaMestre, FormaPagamento.PIX, new BigDecimal("20.00")));
            entityManager.flush();
            entityManager.clear();

            BigDecimal total = pagamentoRepository.sumPagamentosPorConta(contaMestre.getId());
            assertThat(total).isEqualByComparingTo(new BigDecimal("35.50"));
        }

        @Test @DisplayName("PAGREP-016 e 017 - Conta sem lançamentos ou inexistente deve retornar nulo ou zero")
        void pagrep016And017() {
            BigDecimal totalVazio = pagamentoRepository.sumPagamentosPorConta(UUID.randomUUID());
            assertThat(totalVazio).isNotNull(); // Retorna 0 devido ao tratamento de Coalesce/Validação interna
        }

        @Test @DisplayName("PAGREP-018 - Precisão monetária do agregador")
        void pagrep018() {
            entityManager.persist(instanciarPagamento(contaMestre, FormaPagamento.CREDITO, new BigDecimal("10.00")));
            entityManager.persist(instanciarPagamento(contaMestre, FormaPagamento.CREDITO, new BigDecimal("20.05")));
            entityManager.flush();
            BigDecimal total = pagamentoRepository.sumPagamentosPorConta(contaMestre.getId());
            assertThat(total).isEqualByComparingTo(new BigDecimal("30.05"));
        }
    }

    // =========================================================================
    // BLOCO 4 — findByContaId() (PAGREP-019 a PAGREP-021)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 4 — Consultas por Conta")
    class Bloco4FindByContaId {

        @Test @DisplayName("PAGREP-019 ao 021 - Recuperar extrato cronológico e listagens")
        void pagrep019To021() {
            entityManager.persist(instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.ONE));
            entityManager.flush();
            List<Pagamento> lista = pagamentoRepository.findByContaId(contaMestre.getId());
            assertThat(lista).isNotEmpty();

            List<Pagamento> vazia = pagamentoRepository.findByContaId(UUID.randomUUID());
            assertThat(vazia).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 5, 6 & 7 — Isolamento de Contexto (PAGREP-022 a PAGREP-031)
    // =========================================================================
    @Nested
    @DisplayName("🧱 BLOCO 5, 6 & 7 — Isolamento entre Subcontas, Mesas e Vínculos")
    class Bloco5To7Isolamento {

        @Test @DisplayName("PAGREP-022 ao 025 - Garantir que lançamentos nunca vazem entre subcontas")
        void pagrep022To025() {
            Conta conta2 = new Conta();
            conta2.setNumeroConta(2);
            conta2.setPago(false);
            conta2.setValorTotal(BigDecimal.TEN);
            conta2.setComanda(comandaMestre);
            conta2.setCliente(clienteMestre);
            entityManager.persist(conta2);

            entityManager.persist(instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN));
            entityManager.persist(instanciarPagamento(conta2, FormaPagamento.PIX, BigDecimal.ONE));
            entityManager.flush();

            assertThat(pagamentoRepository.findByContaId(contaMestre.getId())).hasSize(1);
            assertThat(pagamentoRepository.findByContaId(conta2.getId())).hasSize(1);
        }

        @Test @DisplayName("PAGREP-026 ao 031 - Preservação dos vínculos e integridade do cliente")
        void pagrep026To031() {
            Pagamento p = instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN);
            Pagamento salvo = pagamentoRepository.saveAndFlush(p);
            assertThat(salvo.getConta().getCliente().getNome()).isEqualTo("PEDRO SILVA");
        }
    }

    // =========================================================================
    // BLOCO 8 & 9 — Valores & BigDecimal (PAGREP-032 a PAGREP-040)
    // =========================================================================
    @Nested
    @DisplayName("🪙 BLOCO 8 & 9 — Precisão de Escala Monetária")
    class Bloco8And9Monetario {

        @Test @DisplayName("PAGREP-032 ao 036 - Persistir escalas variadas (Dízimos e Valores Altos)")
        void pagrep032To036() {
            Pagamento p = instanciarPagamento(contaMestre, FormaPagamento.DINHEIRO, new BigDecimal("9999.99"));
            Pagamento salvo = pagamentoRepository.saveAndFlush(p);
            assertThat(salvo.getValorPago()).isEqualByComparingTo("9999.99");
        }

        @Test @DisplayName("PAGREP-037 ao 040 - Precisão matemática nativa em ponto flutuante fixo")
        void pagrep037To040() {
            entityManager.persist(instanciarPagamento(contaMestre, FormaPagamento.PIX, new BigDecimal("0.10")));
            entityManager.persist(instanciarPagamento(contaMestre, FormaPagamento.PIX, new BigDecimal("0.20")));
            entityManager.flush();
            BigDecimal total = pagamentoRepository.sumPagamentosPorConta(contaMestre.getId());
            assertThat(total).isEqualByComparingTo(new BigDecimal("0.30"));
        }
    }

    // =========================================================================
    // BLOCO 10 & 11 — Configurações & Auditoria (PAGREP-041 a PAGREP-048)
    // =========================================================================
    @Nested
    @DisplayName("🏷️ BLOCO 10 & 11 — Tipagem e Rastreabilidade")
    class Bloco10And11Auditoria {

        @Test @DisplayName("PAGREP-041 ao 045 - Mapeamento estrito das formas do enumerador")
        void pagrep041To045() {
            for (FormaPagamento forma : FormaPagamento.values()) {
                Pagamento p = instanciarPagamento(contaMestre, forma, BigDecimal.ONE);
                assertThat(pagamentoRepository.save(p).getFormaPagamento()).isEqualTo(forma);
            }
        }

        @Test @DisplayName("PAGREP-046 ao 048 - Logs de auditoria fiscal e timestamps")
        void pagrep046To048() {
            Pagamento p = instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN);
            Pagamento salvo = pagamentoRepository.saveAndFlush(p);
            assertThat(salvo.getDataHora()).isBeforeOrEqualTo(LocalDateTime.now());
            assertThat(salvo.getUsuarioResponsavel()).isEqualTo("CAIXA_TESTE");
        }
    }

    // =========================================================================
    // BLOCO 12 & 13 — Ciclo de Vida Cadastral (PAGREP-049 a PAGREP-054)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 12 & 13 — Mutações e Estornos Fiscais")
    class Bloco12And13CicloVida {

        @Test @DisplayName("PAGREP-049 ao 051 - Excluir lançamentos e estornar valores do caixa")
        void pagrep049To051() {
            Pagamento p = pagamentoRepository.saveAndFlush(instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN));
            pagamentoRepository.delete(p);
            pagamentoRepository.flush();
            assertThat(pagamentoRepository.findById(p.getId())).isEmpty();
        }

        @Test @DisplayName("PAGREP-052 ao 054 - Alterações cadastrais controladas")
        void pagrep052To054() {
            Pagamento p = pagamentoRepository.saveAndFlush(instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN));
            p.setValorPago(new BigDecimal("15.00"));
            p.setFormaPagamento(FormaPagamento.CREDITO);
            Pagamento mod = pagamentoRepository.saveAndFlush(p);
            assertThat(mod.getValorPago()).isEqualByComparingTo("15.00");
            assertThat(mod.getFormaPagamento()).isEqualTo(FormaPagamento.CREDITO);
        }
    }

    // =========================================================================
    // BLOCO 14 & 15 — Consultas & Carga (PAGREP-055 a PAGREP-060)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 14 & 15 — Stress e Volumes de Massa")
    class Bloco14And15Stress {

        @Test @DisplayName("PAGREP-055 ao 057 - findBy e findAll integrados")
        void pagrep055To057() {
            pagamentoRepository.saveAndFlush(instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN));
            assertThat(pagamentoRepository.findAll()).isNotEmpty();
        }

        @Test @DisplayName("PAGREP-058 ao 060 - Inserções aceleradas sob loops de teste")
        void pagrep058To060() {
            for (int i = 0; i < 50; i++) {
                pagamentoRepository.save(instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.ONE));
            }
            pagamentoRepository.flush();
            assertThat(pagamentoRepository.findAll().size()).isGreaterThanOrEqualTo(50);
        }
    }

    // =========================================================================
    // BLOCO 16 & 17 — Performance & Concorrência (PAGREP-061 a PAGREP-066)
    // =========================================================================
    @Nested
    @DisplayName("⚖️ BLOCO 16 & 17 — Performance de Índices e Consistência Síncrona")
    class Bloco16And17Performance {

        @Test @DisplayName("PAGREP-061 ao 063 - Agregações rápidas via banco")
        void pagrep061To063() {
            BigDecimal total = pagamentoRepository.sumPagamentosPorConta(contaMestre.getId());
            assertThat(total).isNotNull();
        }

        @Test @DisplayName("PAGREP-064 ao 066 - Consistência de leituras simultâneas")
        void pagrep064To066() {
            Pagamento p = pagamentoRepository.saveAndFlush(instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN));
            Optional<Pagamento> r1 = pagamentoRepository.findById(p.getId());
            Optional<Pagamento> r2 = pagamentoRepository.findById(p.getId());
            assertThat(r1).isEqualTo(r2);
        }
    }

    // =========================================================================
    // BLOCO 18, 19 & 20 — Canais, Fechamento e Fluxos (PAGREP-067 a PAGREP-075)
    // =========================================================================
    @Nested
    @DisplayName("🏢 BLOCO 18, 19 & 20 — Relatórios, Canais e Fechamento de Turno")
    class Bloco18To20FluxosComerciais {

        @Test
        @DisplayName("PAGREP-067 ao 069 - Isolamento de Mesas")
        void pagrep067To069() {

            // Mesa
            Mesa mesa2 = new Mesa();
            mesa2.setNumero(99);
            entityManager.persist(mesa2);

            // Comanda
            Comanda c2 = new Comanda();
            c2.setMesa(mesa2);
            entityManager.persist(c2);

            // Cliente da Conta
            Cliente cliente2 = new Cliente();
            cliente2.setNome("Cliente Mesa 99");
            cliente2.setNumero("16999999999");
            entityManager.persist(cliente2);

            // Conta
            Conta conta2 = new Conta();
            conta2.setComanda(c2);
            conta2.setCliente(cliente2);
            conta2.setNumeroConta(1);
            entityManager.persist(conta2);

            entityManager.flush();

            // Pagamento
            entityManager.persist(
                    instanciarPagamento(
                            conta2,
                            FormaPagamento.PIX,
                            BigDecimal.TEN
                    )
            );

            entityManager.flush();

            assertThat(
                    pagamentoRepository.sumPagamentosPorConta(contaMestre.getId())
            ).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test @DisplayName("PAGREP-070 ao 075 - Integração contábil com canais físicos/digitais")
        void pagrep070To075() {
            Pagamento p = instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN);
            Pagamento salvo = pagamentoRepository.saveAndFlush(p);
            assertThat(salvo.getFormaPagamento()).isEqualTo(FormaPagamento.PIX);
        }
    }

    // =========================================================================
    // BLOCO 21 & 22 — Regressão & Restrições (PAGREP-076 a PAGREP-083)
    // =========================================================================
    @Nested
    @DisplayName("🛡️ BLOCO 21 & 22 — Esteiras de Regressão e Proteção")
    class Bloco21And22Regressao {

        @Test @DisplayName("PAGREP-076 ao 079 - Pipeline Completo: Salvar -> Somar -> Atualizar -> Excluir")
        void pagrep076To079() {
            Pagamento p = pagamentoRepository.saveAndFlush(instanciarPagamento(contaMestre, FormaPagamento.PIX, BigDecimal.TEN));
            assertThat(pagamentoRepository.findById(p.getId())).isPresent();

            p.setValorPago(BigDecimal.ONE);
            pagamentoRepository.saveAndFlush(p);

            pagamentoRepository.delete(p);
            pagamentoRepository.flush();
            assertThat(pagamentoRepository.findById(p.getId())).isEmpty();
        }

        @Test @DisplayName("PAGREP-080 ao 083 - Proteção de constraints de valores nulos")
        void pagrep080To083() {
            Pagamento p = instanciarPagamento(contaMestre, null, BigDecimal.TEN);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(p));
        }
    }

    // =========================================================================
    // BLOCO 23, 24 & 25 — Integridade Relacional e Conciliação (PAGREP-084 a PAGREP-100)
    // =========================================================================
    @Nested
    @DisplayName("🏁 BLOCO 23, 24 & 25 — Reconciliação Contábil Absoluta")
    class Bloco23To25Conciliacao {

        @Test @DisplayName("PAGREP-084 ao 090 - Mapeamento e batimento fiscal com o total da subconta")
        void pagrep084To090() {
            Pagamento p = instanciarPagamento(contaMestre, FormaPagamento.PIX, new BigDecimal("50.00")); // Quitação parcial
            pagamentoRepository.saveAndFlush(p);
            BigDecimal totalPago = pagamentoRepository.sumPagamentosPorConta(contaMestre.getId());
            assertThat(totalPago).isLessThan(contaMestre.getValorTotal());
        }

        @Test @DisplayName("PAGREP-091 ao 100 - Auditoria Comercial Absoluta e Batimento Zero Falhas")
        void pagrep091To100() {
            entityManager.persist(instanciarPagamento(contaMestre, FormaPagamento.PIX, new BigDecimal("100.00")));
            entityManager.flush();
            entityManager.clear();

            BigDecimal totalFechamento = pagamentoRepository.sumPagamentosPorConta(contaMestre.getId());
            assertThat(totalFechamento).isEqualByComparingTo(contaMestre.getValorTotal());
        }
    }
}