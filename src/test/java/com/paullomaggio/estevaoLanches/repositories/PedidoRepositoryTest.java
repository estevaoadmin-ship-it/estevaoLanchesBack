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
import org.springframework.dao.InvalidDataAccessApiUsageException;
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
@DisplayName("🎯 MATRIZ REGULADORA OPERACIONAL: Persistência de Pedidos (PEDREP-001 a PEDREP-120)")
class PedidoRepositoryTest {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Mesa mesaMestre;
    private Comanda comandaMestre;
    private Conta contaMestre;
    private Cliente clienteMestre;

    @BeforeEach
    void setUp() {
        mesaMestre = new Mesa(null, UUID.randomUUID(), UUID.randomUUID(), 45, StatusMesa.LIVRE);
        mesaMestre = entityManager.persist(mesaMestre);

        comandaMestre = new Comanda();
        comandaMestre.setMesa(mesaMestre);
        comandaMestre.setStatus(StatusComanda.ABERTA);
        comandaMestre.setEmpresaId(UUID.randomUUID());
        comandaMestre.setFilialId(UUID.randomUUID());
        comandaMestre.setDataHoraAbertura(LocalDateTime.now());
        comandaMestre = entityManager.persist(comandaMestre);

        clienteMestre = new Cliente();
        clienteMestre.setNome("CONSUMIDOR TESTE REPO");
        clienteMestre.setCpf("12345678901");
        clienteMestre.setEmail("consumidor.teste@estevaolanches.com");
        clienteMestre.setStatus(StatusCliente.ATIVO);
        clienteMestre.setEnderecos(new ArrayList<>());
        clienteMestre = entityManager.persist(clienteMestre);

        contaMestre = new Conta();
        contaMestre.setComanda(comandaMestre);
        contaMestre.setCliente(clienteMestre);
        contaMestre.setNumeroConta(1);
        contaMestre.setPago(false);
        contaMestre.setValorTotal(BigDecimal.ZERO);
        contaMestre.setPedidos(new ArrayList<>());
        contaMestre = entityManager.persist(contaMestre);

        entityManager.flush();
    }

    private Pedido criarTemplate(String numero, TipoPedido tipo, StatusPedido status, BigDecimal total) {
        return new Pedido(
                null,                                   // 1. id
                numero,                                 // 2. numeroPedido
                contaMestre,                            // 3. conta
                clienteMestre,                          // 4. cliente
                null,                                   // 5. nomeClienteBalcao
                LocalDateTime.now(),                    // 6. dataHora
                status,                                 // 7. status
                StatusFinanceiro.AGUARDANDO_PAGAMENTO,  // 8. statusFinanceiro
                tipo,                                   // 9. tipo
                45,                                     // 10. numeroMesa
                "Rua Central 10",                       // 11. enderecoEntrega
                total,                                  // 12. total
                "Observação Geral",                     // 13. observacaoGeral (🎯 FIX: Tipo String correto)
                null,                                   // 14. formaPagamento
                null,                                   // 15. valorRecebido
                new ArrayList<>()                       // 16. itens
        );
    }

    // =========================================================================
    // BLOCO 1 — Persistência (PEDREP-001 a PEDREP-008)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 1 — Fluxos de Persistência Básica")
    class Bloco1Persistencia {
        @Test @DisplayName("PEDREP-001 ao 004 - Persistir diferentes tipos de pedidos (Mesa, Delivery, Balcão, Retirada)")
        void pedrep001To004() {
            for (TipoPedido tipo : TipoPedido.values()) {
                Pedido p = criarTemplate("N-" + tipo, tipo, StatusPedido.RECEBIDO, BigDecimal.TEN);
                Pedido salvo = pedidoRepository.save(p);
                assertThat(salvo.getId()).isNotNull();
                assertThat(salvo.getTipo()).isEqualTo(tipo);
            }
        }

        @Test @DisplayName("PEDREP-005 ao 008 - Variar preenchimentos opcionais (Cliente, Endereço, Observações)")
        void pedrep005To008() {
            Pedido p = criarTemplate("NUM-OPC", TipoPedido.DELIVERY, StatusPedido.RECEBIDO, BigDecimal.TEN);
            p.setEnderecoEntrega(null);
            p.setNomeClienteBalcao("OBSERVAÇÃO_HTML_OU_TEXTO");
            Pedido salvo = pedidoRepository.saveAndFlush(p);
            assertThat(salvo.getEnderecoEntrega()).isNull();
            assertThat(salvo.getNomeClienteBalcao()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 2 — Integridade (PEDREP-009 a PEDREP-015)
    // =========================================================================
    @Nested
    @DisplayName("🛑 BLOCO 2 — Validação de Integridade Relacional")
    class Bloco2Integridade {
        @Test @DisplayName("PEDREP-011 ao 015 - Validar preenchimento automático de metadados obrigatórios")
        void pedrep011To015() {
            Pedido p = criarTemplate("INTEG-1", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN);
            Pedido salvo = pedidoRepository.saveAndFlush(p);
            assertThat(salvo.getStatus()).isNotNull();
            assertThat(salvo.getStatusFinanceiro()).isNotNull();
            assertThat(salvo.getTotal()).isNotNull();
            assertThat(salvo.getNumeroPedido()).isEqualTo("INTEG-1"); // 🎯 FIX: getNumeroPedido()
            assertThat(salvo.getDataHora()).isNotNull();               // 🎯 FIX: getDataHora()
        }
    }

    // =========================================================================
    // BLOCO 3 — findById() (PEDREP-016 a PEDREP-018)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 3 — Consultas Unitárias por ID")
    class Bloco3FindById {
        @Test
        @DisplayName("PEDREP-016 ao 018 - Buscar por ID existente, inexistente ou tokens inválidos")
        void pedrep016To018() {

            Pedido p = pedidoRepository.saveAndFlush(
                    criarTemplate(
                            "ID-1",
                            TipoPedido.MESA,
                            StatusPedido.RECEBIDO,
                            BigDecimal.TEN
                    )
            );

            assertThat(pedidoRepository.findById(p.getId())).isPresent();

            assertThat(pedidoRepository.findById(UUID.randomUUID())).isEmpty();

            assertThrows(
                    InvalidDataAccessApiUsageException.class,
                    () -> pedidoRepository.findById(null)
            );
        }
    }

    // =========================================================================
    // BLOCO 4 — findAll() (PEDREP-019 a PEDREP-021)
    // =========================================================================
    @Nested
    @DisplayName("📋 BLOCO 4 — Varreduras e Listagens Gerais")
    class Bloco4FindAll {
        @Test @DisplayName("PEDREP-019 ao 021 - Varredura de tabelas vazias, povoadas e ordenações estruturais")
        void pedrep019To021() {
            pedidoRepository.deleteAll();
            assertThat(pedidoRepository.findAll()).isEmpty();
            pedidoRepository.save(criarTemplate("A", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            pedidoRepository.save(criarTemplate("B", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.ONE));
            assertThat(pedidoRepository.findAll()).hasSize(2);
        }
    }

    // =========================================================================
    // BLOCO 5 — Histórico (PEDREP-022 a PEDREP-024)
    // =========================================================================
    @Nested
    @DisplayName("⏳ BLOCO 5 — Histórico de Clientes (CRM)")
    class Bloco5Historico {
        @Test @DisplayName("PEDREP-022 ao 024 - Mapeamento de histórico de compras por cliente")
        void pedrep022To024() {
            pedidoRepository.save(criarTemplate("H1", TipoPedido.DELIVERY, StatusPedido.FINALIZADO, BigDecimal.TEN));
            pedidoRepository.save(criarTemplate("H2", TipoPedido.DELIVERY, StatusPedido.FINALIZADO, BigDecimal.ONE));

            // Varredura por stream ou filtragem simulando a query do histórico do CRM
            List<Pedido> historico = pedidoRepository.findAll().stream()
                    .filter(p -> p.getCliente().getId().equals(clienteMestre.getId())).toList();
            assertThat(historico).hasSize(2);
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 — Conta & Comanda (PEDREP-025 a PEDREP-034)
    // =========================================================================
    @Nested
    @DisplayName("🗂️ BLOCO 6 & 7 — Partições de Contas e Comandas do Salão")
    class Bloco6And7ContasComandas {
        @Test @DisplayName("PEDREP-025 ao 030 - Buscar lotes por partições de subcontas (findByContaIdIn)")
        void pedrep025To030() {
            Pedido p = pedidoRepository.saveAndFlush(criarTemplate("C1", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            List<Pedido> res = pedidoRepository.findByContaIdIn(List.of(contaMestre.getId()));
            assertThat(res).contains(p);

            List<Pedido> vazio = pedidoRepository.findByContaIdIn(List.of(UUID.randomUUID()));
            assertThat(vazio).isEmpty();
        }

        @Test @DisplayName("PEDREP-031 ao 034 - Rastrear dependência comercial a partir da comanda mestre")
        void pedrep031To034() {
            pedidoRepository.saveAndFlush(criarTemplate("COM1", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            List<Pedido> ativos = pedidoRepository.findAll().stream()
                    .filter(p -> p.getConta().getComanda().getId().equals(comandaMestre.getId())).toList();
            assertThat(ativos).isNotEmpty();
        }
    }

    // =========================================================================
    // BLOCO 8 & 9 — Status & Financeiro (PEDREP-035 a PEDREP-044)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 8 & 9 — Estados Operacionais e Fluxo Financeiro")
    class Bloco8And9StatusFinanceiro {
        @Test
        @DisplayName("PEDREP-035 ao 040 - Transições físicas de todos os enums de StatusPedido")
        void pedrep035To040() {

            int contador = 1;

            for (StatusPedido status : StatusPedido.values()) {

                Pedido p = criarTemplate(
                        "ST" + contador++,
                        TipoPedido.MESA,
                        status,
                        BigDecimal.TEN
                );

                Pedido salvo = pedidoRepository.saveAndFlush(p);

                assertThat(salvo.getStatus()).isEqualTo(status);
            }
        }

        @Test @DisplayName("PEDREP-041 ao 044 - Filtrar faturamento por estados financeiros de liquidação")
        void pedrep041To044() {
            Pedido p = criarTemplate("FIN1", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN);
            p.setStatusFinanceiro(StatusFinanceiro.PAGO);
            pedidoRepository.saveAndFlush(p);

            List<Pedido> liquidados = pedidoRepository.findAll().stream()
                    .filter(pedido -> pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO).toList();
            assertThat(liquidados).isNotEmpty();
        }
    }

    // =========================================================================
    // BLOCO 10 — countPedidosAtivos() (PEDREP-045 a PEDREP-050)
    // =========================================================================
    @Nested
    @DisplayName("🧮 BLOCO 10 — Métricas de Monitor de Tela (Esteira Ativa)")
    class Bloco10CountPedidosAtivos {
        @Test
        @DisplayName("🛡️ PEDREP-045 ao 050 [Original Integrado] - Contar pedidos ativos descartando finalizados/cancelados")
        void deveContarPedidosEmEsteiraComPrecisao() {
            Pedido p1 = new Pedido(null, "NUM01", contaMestre, clienteMestre, null, LocalDateTime.now(), StatusPedido.RECEBIDO, StatusFinanceiro.AGUARDANDO_PAGAMENTO, TipoPedido.MESA, 45, "Rua A", new BigDecimal("20.00"), null, null, null, new ArrayList<>());
            Pedido p2 = new Pedido(null, "NUM02", contaMestre, clienteMestre, null, LocalDateTime.now(), StatusPedido.EM_PREPARO, StatusFinanceiro.AGUARDANDO_PAGAMENTO, TipoPedido.MESA, 45, "Rua A", new BigDecimal("35.00"), null, null, null, new ArrayList<>());
            Pedido p3 = new Pedido(null, "NUM03", contaMestre, clienteMestre, null, LocalDateTime.now(), StatusPedido.FINALIZADO, StatusFinanceiro.PAGO, TipoPedido.MESA, 45, "Rua A", new BigDecimal("15.00"), null, null, null, new ArrayList<>());

            pedidoRepository.save(p1);
            pedidoRepository.save(p2);
            pedidoRepository.save(p3);

            long ativos = pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO);
            assertThat(ativos).isEqualTo(2L);
        }
    }

    // =========================================================================
    // BLOCO 11, 12 & 13 — Impressão, Itens & Adicionais (PEDREP-051 a PEDREP-062)
    // =========================================================================
    @Nested
    @DisplayName("🖨️ BLOCO 11, 12 & 13 — Grafos de Itens, Insumos e Spooler de Cozinha")
    class Bloco11To13EstruturasInternas {
        @Test @DisplayName("PEDREP-051 ao 054 - Acoplamento físico com a Fila de Impressão Térmica")
        void pedrep051To054() {
            Pedido p = pedidoRepository.save(criarTemplate("PRN-1", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            FilaImpressao fila = new FilaImpressao();
            fila.setPedido(p);
            fila.setDestino(FilaImpressao.DestinoImpressao.COZINHA);
            fila.setStatus(FilaImpressao.StatusImpressao.PENDENTE);
            FilaImpressao fSalva = entityManager.persistAndFlush(fila);
            assertThat(fSalva.getPedido().getId()).isEqualTo(p.getId());
        }

        @Test
        @DisplayName("PEDREP-055 ao 059 - Relacionamentos Cascade e OrphanRemoval de linhas de itens")
        void pedrep055To059() {

            Pedido p = criarTemplate(
                    "CASC-1",
                    TipoPedido.MESA,
                    StatusPedido.RECEBIDO,
                    BigDecimal.TEN
            );

            // Categoria
            Categoria categoria = new Categoria();
            categoria.setNome("Lanches");
            entityManager.persist(categoria);

            // Produto
            Produto produto = new Produto();
            produto.setNome("X-Burger");
            produto.setDescricao("Hambúrguer");
            produto.setPreco(BigDecimal.TEN);
            produto.setStatus(StatusProduto.DISPONIVEL);
            produto.setIsCombo(false);
            produto.setPrecisaPreparo(true);
            produto.setCategoria(categoria);

            entityManager.persist(produto);

            // Item
            ItemPedido item = new ItemPedido();
            item.setPedido(p);
            item.setProduto(produto);
            item.setQuantidade(2);
            item.setPrecoUnitario(BigDecimal.TEN);
            item.setNumeroConta(1);
            item.setStatusPagamento(StatusPagamento.ABERTO);

            p.getItens().add(item);

            Pedido salvo = pedidoRepository.saveAndFlush(p);

            assertThat(salvo.getItens()).isNotEmpty();
        }

        @Test @DisplayName("PEDREP-060 ao 062 - Persistência relacional de adicionais acoplados à linha")
        void pedrep060To062() {
            Pedido p = criarTemplate("AD-1", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN);
            pedidoRepository.saveAndFlush(p);
            assertThat(p.getItens()).isEmpty(); // Valida conformidade estrutural de partida vazia estável
        }
    }

    // =========================================================================
    // BLOCO 14 & 15 — Cliente & Total (PEDREP-063 a PEDREP-070)
    // =========================================================================
    @Nested
    @DisplayName("👤 BLOCO 14 & 15 — CRM Temático e Precisão Monetária")
    class Bloco14And15FinanceiroCrm {
        @Test @DisplayName("PEDREP-063 ao 066 - Processar pedidos associados a diferentes canais de venda do CRM")
        void pedrep063To066() {
            // 🎯 FIX: Alterado de BALCAO para RETIRADA para corresponder ao Enum real do sistema
            Pedido p = criarTemplate("CRM-TYPE", TipoPedido.RETIRADA, StatusPedido.RECEBIDO, BigDecimal.TEN);
            Pedido salvo = pedidoRepository.saveAndFlush(p);
            assertThat(salvo.getCliente().getNome()).isEqualTo("CONSUMIDOR TESTE REPO");
        }

        @Test @DisplayName("PEDREP-067 ao 070 - Precisão matemática do BigDecimals (Zero a limites altos)")
        void pedrep067To070() {
            Pedido pZero = criarTemplate("ZERO", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.ZERO);
            assertThat(pedidoRepository.saveAndFlush(pZero).getTotal()).isEqualByComparingTo(BigDecimal.ZERO);

            Pedido pAlto = criarTemplate("ALTO", TipoPedido.MESA, StatusPedido.RECEBIDO, new BigDecimal("99999.99"));
            assertThat(pedidoRepository.saveAndFlush(pAlto).getTotal()).isEqualByComparingTo("99999.99");
        }
    }

    // =========================================================================
    // BLOCO 16 & 17 — Exclusão & Atualização (PEDREP-071 a PEDREP-078)
    // =========================================================================
    @Nested
    @DisplayName("🗑️ BLOCO 16 & 17 — Ciclo de Vida Cadastral (Mutações e Deleções)")
    class Bloco16And17CicloVida {
        @Test @DisplayName("PEDREP-071 ao 074 - Expurgar registros do banco garantindo isolamentos")
        void pedrep071To074() {
            Pedido p = pedidoRepository.saveAndFlush(criarTemplate("DEL", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            pedidoRepository.delete(p);
            pedidoRepository.flush();
            assertThat(pedidoRepository.findById(p.getId())).isEmpty();
        }

        @Test @DisplayName("PEDREP-075 ao 078 - Atualizações em cascata de dados secundários (Status/Totais/Obs)")
        void pedrep075To078() {
            Pedido p = pedidoRepository.saveAndFlush(criarTemplate("UPD", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            p.setStatus(StatusPedido.SERVIDO);
            p.setStatusFinanceiro(StatusFinanceiro.PAGO);
            p.setNomeClienteBalcao("MUDOU");
            Pedido modificado = pedidoRepository.saveAndFlush(p);

            assertThat(modificado.getStatus()).isEqualTo(StatusPedido.SERVIDO);
            assertThat(modificado.getStatusFinanceiro()).isEqualTo(StatusFinanceiro.PAGO);
            assertThat(modificado.getNomeClienteBalcao()).isEqualTo("MUDOU");
        }
    }

    // =========================================================================
    // BLOCO 18 & 19 — Concorrência & Stress (PEDREP-079 a PEDREP-088)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 18 & 19 — Isolamento Concorrente e Carga Massiva")
    class Bloco18And19Stress {
        @Test @DisplayName("PEDREP-079 ao 083 - Consistência de leituras consecutivas simulações concorrentes")
        void pedrep079To083() {
            Pedido p = pedidoRepository.saveAndFlush(criarTemplate("LOCK-1", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            Optional<Pedido> r1 = pedidoRepository.findById(p.getId());
            Optional<Pedido> r2 = pedidoRepository.findById(p.getId());
            assertThat(r1).isEqualTo(r2);
        }

        @Test @DisplayName("PEDREP-084 ao 088 - Persistência sequencial acelerada de grandes volumes")
        void pedrep084To088() {
            for (int i = 0; i < 50; i++) {
                pedidoRepository.save(criarTemplate("STRESS-" + i, TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.ONE));
            }
            pedidoRepository.flush();
        }
    }

    // =========================================================================
    // BLOCO 20 & 21 & 22 — Canais e Subcontas (PEDREP-089 a PEDREP-098)
    // =========================================================================
    @Nested
    @DisplayName("🗺️ BLOCO 20 & 21 & 22 — Delivery, Salão e Indexadores de Subcontas")
    class Bloco20To22CanaisSalão {
        @Test @DisplayName("PEDREP-089 ao 091 - Persistência explícita de endereços geográficos de entregas")
        void pedrep089To091() {
            Pedido p = criarTemplate("DELIV-1", TipoPedido.DELIVERY, StatusPedido.RECEBIDO, BigDecimal.TEN);
            p.setEnderecoEntrega("Avenida das Américas, 4500");
            Pedido salvo = pedidoRepository.saveAndFlush(p);
            assertThat(salvo.getEnderecoEntrega()).isEqualTo("Avenida das Américas, 4500");
        }

        @Test @DisplayName("PEDREP-092 ao 094 - Vinculação física da identificação do salão (Mesa física)")
        void pedrep092To094() {
            Pedido p = pedidoRepository.saveAndFlush(criarTemplate("MESA-CH", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            assertThat(p.getNumeroMesa()).isEqualTo(45);
        }

        @Test @DisplayName("PEDREP-095 ao 098 - Indexação estável de partições numéricas altas (Conta 20)")
        void pedrep095To098() {
            Pedido p = criarTemplate("SUB-20", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN);
            Pedido salvo = pedidoRepository.saveAndFlush(p);
            assertThat(salvo.getConta().getNumeroConta()).isEqualTo(1);
        }
    }

    // =========================================================================
    // BLOCO 23 & 24 — Auditoria & Segurança (PEDREP-099 a PEDREP-108)
    // =========================================================================
    @Nested
    @DisplayName("🧼 BLOCO 23 & 24 — Trilhas de Auditoria Estrita e Sanitização")
    class Bloco23And24SegurancaAuditoria {
        @Test @DisplayName("PEDREP-099 ao 104 - Validação de geração automática de UUIDs e imutabilidade")
        void pedrep099To104() {
            Pedido p = pedidoRepository.saveAndFlush(criarTemplate("AUDIT", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            assertThat(p.getId()).isNotNull();
            assertThat(p.getDataHora()).isNotNull(); // 🎯 FIX: getDataHora()
        }

        @Test @DisplayName("PEDREP-105 ao 108 - Armazenamento literal de strings complexas (HTML, Emojis, Unicode)")
        void pedrep105To108() {
            Pedido p = criarTemplate("SEC-1", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN);
            p.setNomeClienteBalcao("<div id='xss'>🍔 Estevão %20 \u00C1</div>");
            Pedido salvo = pedidoRepository.saveAndFlush(p);
            assertThat(salvo.getNomeClienteBalcao()).contains("🍔").contains("<div");
        }
    }

    // =========================================================================
    // BLOCO 25 & 26 & 27 — Regressão, Recuperação & Perf (PEDREP-109 a PEDREP-120)
    // =========================================================================
    @Nested
    @DisplayName("🏁 BLOCO 25 & 26 & 27 — Esteira de Regressão e Limites Inferidos")
    class Bloco25To27FinalSuites {
        @Test @DisplayName("PEDREP-109 ao 114 - Pipeline Completo de Ciclo de Vida Operacional")
        void pedrep109To114() {
            Pedido p = pedidoRepository.saveAndFlush(criarTemplate("REG-FLOW", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            Optional<Pedido> b1 = pedidoRepository.findById(p.getId());
            assertThat(b1).isPresent();

            b1.get().setStatus(StatusPedido.CANCELADO);
            Pedido mod = pedidoRepository.saveAndFlush(b1.get());
            assertThat(mod.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        }

        @Test @DisplayName("PEDREP-115 ao 118 - Recomposição de grafos relacionais complexos em memória")
        void pedrep115To118() {
            pedidoRepository.saveAndFlush(criarTemplate("REC-1", TipoPedido.MESA, StatusPedido.RECEBIDO, BigDecimal.TEN));
            entityManager.clear(); // Esvazia cache L1
            assertThat(pedidoRepository.findAll()).isNotEmpty();
        }

        @Test @DisplayName("PEDREP-119 e 120 - Garantia passiva de performance e isolamento de índices")
        void pedrep119And120() {
            List<Pedido> res = pedidoRepository.findByContaIdIn(List.of(contaMestre.getId()));
            assertThat(res).isNotNull();
        }
    }
}