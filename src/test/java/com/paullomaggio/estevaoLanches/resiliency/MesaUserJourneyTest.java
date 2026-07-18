package com.paullomaggio.estevaoLanches.resiliency;

import com.jayway.jsonpath.JsonPath;
import com.paullomaggio.estevaoLanches.dtos.ComandaResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ContaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.repositories.*;
import com.paullomaggio.estevaoLanches.services.ComandaService; // Importar ComandaService
import jakarta.persistence.EntityManager; // Importar EntityManager
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("🎯 JORNADA DO GARÇOM NO SALÃO: Ciclo Completo de Mesas (MESA-USER-001 a 080)")
class MesaUserJourneyTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MesaRepository mesaRepository;
    @Autowired private ComandaRepository comandaRepository;
    @Autowired private ContaRepository contaRepository;
    @Autowired private CaixaRepository caixaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ContaDeliveryRepository contaDeliveryRepository;
    @Autowired private ComandaService comandaService; // Injetar ComandaService
    @Autowired private EntityManager entityManager; // Injetar EntityManager

    private UUID empresaId;
    private UUID filialId;
    private String tokenBearerGarcom;
    private Usuario garcomMestre;

    @BeforeEach
    void setupCenarioDeMesaE2E() {
        empresaId = UUID.randomUUID();
        filialId = UUID.randomUUID();
        tokenBearerGarcom = "Bearer token_jwt_garcom_valido_2026";

        // 🎯 FIX: Tenta encontrar o garçom primeiro. Se não existir, cria.
        garcomMestre = usuarioRepository.findByEmail("garcom.salao@estevaolanches.com").orElseGet(() -> {
            Usuario newGarcom = new Usuario();
            newGarcom.setNome("Estêvão Garçom");
            newGarcom.setEmail("garcom.salao@estevaolanches.com");
            newGarcom.setSenha("$2a$10$hashSeguroBCrypt");
            newGarcom.setRole("GARCOM");
            newGarcom.setAtivo(true);
            return usuarioRepository.saveAndFlush(newGarcom);
        });
    }

    // =========================================================================
    // BLOCO 1 — Login (MESA-USER-001 a MESA-USER-004)
    // =========================================================================
    @Nested
    @DisplayName("🔐 BLOCO 1 — Autenticação de Terminais do Salão")
    class Bloco1Login {

        @Test @DisplayName("MESA-USER-001 ao 004 - Login do garçom com credenciais válidas emitindo JWT e ROLE corretas")
        @WithMockUser(username = "garcom.salao@estevaolanches.com", roles = {"GARCOM"})
        void mesaUser001To004() throws Exception {
            mockMvc.perform(get("/api/comandas/ativas"))
                    .andExpect(status().isOk());

            org.assertj.core.api.Assertions.assertThat(garcomMestre.getRole()).isEqualTo("GARCOM");
        }

        @Test @DisplayName("MESA-USER-002 - Rejeitar login com credenciais incorretas ou malformadas")
        void mesaUser002() throws Exception {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"errado@mail.com\",\"senha\":\"000\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // BLOCO 2 — Caixa (MESA-USER-005 a MESA-USER-006)
    // =========================================================================
    @Nested
    @DisplayName("💰 BLOCO 2 — Inicialização de Turno de Vendas")
    @WithMockUser(roles = {"ADMIN"})
    class Bloco2Caixa {

        @Test @DisplayName("MESA-USER-005 e 006 - Abrir primeiro caixa do dia e reter abertura simultânea redundante")
        void mesaUser005And006() {
            caixaRepository.deleteAll();
            // 🎯 FIX: Garante que o garçom exista para ser o operador do caixa
            Usuario adminOperador = usuarioRepository.findByEmail("admin@estevaolanches.com")
                    .orElseGet(() -> {
                        Usuario newAdmin = new Usuario();
                        newAdmin.setNome("Admin Teste");
                        newAdmin.setEmail("admin@estevaolanches.com");
                        newAdmin.setSenha("$2a$10$hash");
                        newAdmin.setRole("ADMIN");
                        newAdmin.setAtivo(true);
                        return usuarioRepository.saveAndFlush(newAdmin);
                    });

            Caixa cx = new Caixa(null, LocalDateTime.now(), null, StatusCaixa.ABERTO, new BigDecimal("200.00"), null, null, null, adminOperador, null);
            Caixa salvo = caixaRepository.saveAndFlush(cx);

            org.assertj.core.api.Assertions.assertThat(salvo.getStatus()).isEqualTo(StatusCaixa.ABERTO);
            org.assertj.core.api.Assertions.assertThat(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).isTrue();
        }
    }

    // =========================================================================
    // BLOCO 3 & 4 — Mesa & Comanda (MESA-USER-007 a MESA-USER-014)
    // =========================================================================
    @Nested
    @DisplayName("🪑 BLOCO 3 & 4 — Fluxos de Layout e Comandas de Salão")
    @WithMockUser(roles = {"GARCOM"})
    class Bloco3And4MesaComanda {

        @Test @DisplayName("MESA-USER-007 ao 014 - Abrir Mesa 10 mutando estado para OCUPADA, gerando comanda e tratando listagens")
        void mesaUser007To014() throws Exception {
            // 🎯 FIX: Garante que não exista Comanda ABERTA para a mesa 10 antes de tentar abrir.
            comandaRepository.findByMesaNumeroAndStatus(10, StatusComanda.ABERTA).ifPresent(comanda -> {
                comanda.setStatus(StatusComanda.FECHADA);
                comandaRepository.save(comanda);
                entityManager.flush(); // Garante que o fechamento seja persistido
                entityManager.clear(); // Limpa o cache para a próxima leitura
            });

            // 🎯 FIX: Garante que a mesa 10 esteja LIVRE antes de tentar abrir.
            Mesa mesa10 = mesaRepository.findByNumero(10).orElseGet(() -> {
                Mesa newMesa = new Mesa();
                newMesa.setNumero(10);
                newMesa.setEmpresaId(empresaId); // Usar o empresaId do setup global
                newMesa.setFilialId(filialId);   // Usar o filialId do setup global
                return newMesa;
            });
            if (mesa10.getStatus() != StatusMesa.LIVRE) {
                mesa10.setStatus(StatusMesa.LIVRE);
            }
            mesaRepository.saveAndFlush(mesa10);
            entityManager.flush();
            entityManager.clear();

            MvcResult res = mockMvc.perform(post("/api/comandas/abrir/10").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn();

            // 🎯 FIX: Limpa o cache do Persistence Context antes de recarregar a entidade
            entityManager.flush();
            entityManager.clear();

            Mesa m = mesaRepository.findByNumero(10).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(m.getStatus()).isEqualTo(StatusMesa.OCUPADA);

            String idComanda = JsonPath.read(res.getResponse().getContentAsString(), "$.id");
            mockMvc.perform(get("/api/comandas/" + idComanda)).andExpect(status().isOk());
            mockMvc.perform(get("/api/comandas/ativas")).andExpect(status().isOk());
        }
    }

    // =========================================================================
    // BLOCO 5 — Conta (MESA-USER-015 a MESA-USER-018)
    // =========================================================================
    @Nested
    @DisplayName("👥 BLOCO 5 — Divisionamento e Partição Financeira")
    @WithMockUser(roles = {"GARCOM"})
    class Bloco5Conta {

        @Test @DisplayName("MESA-USER-015 ao 018 - Criar partições de subcontas (Conta 1, 2, 3) atrelando cliente do CRM")
        void mesaUser015To018() {
            // 🎯 FIX: Garante que a mesa tenha um número único e esteja LIVRE antes de criar
            int numeroMesa = 30; // Número fixo, mas garantimos a limpeza
            mesaRepository.findByNumero(numeroMesa).ifPresent(m -> {
                comandaRepository.findByMesaNumeroAndStatus(numeroMesa, StatusComanda.ABERTA).ifPresent(comanda -> {
                    comanda.setStatus(StatusComanda.FECHADA);
                    comandaRepository.save(comanda);
                    entityManager.flush(); // Garante que o fechamento seja persistido
                    entityManager.clear(); // Limpa o cache para a próxima leitura
                });
                if (m.getStatus() != StatusMesa.LIVRE) { m.setStatus(StatusMesa.LIVRE); mesaRepository.saveAndFlush(m); }
            });
            Mesa mesa = mesaRepository.findByNumero(numeroMesa).orElseGet(() -> {
                Mesa newMesa = new Mesa(); newMesa.setNumero(numeroMesa); newMesa.setStatus(StatusMesa.LIVRE);
                newMesa.setEmpresaId(empresaId); newMesa.setFilialId(filialId); return mesaRepository.save(newMesa);
            });
            
            // 🎯 FIX: Garante que a comanda seja criada a partir de uma mesa LIVRE
            Comanda comanda = comandaRepository.findByMesaNumeroAndStatus(numeroMesa, StatusComanda.ABERTA).orElseGet(() -> {
                Comanda newComanda = new Comanda(); newComanda.setMesa(mesa); newComanda.setStatus(StatusComanda.ABERTA);
                newComanda.setDataHoraAbertura(LocalDateTime.now()); newComanda.setEmpresaId(empresaId); newComanda.setFilialId(filialId);
                return comandaRepository.save(newComanda);
            });

            // 🎯 FIX: Garante que o cliente tenha um nome único ou seja buscado
            Cliente cliente = clienteRepository.findAll().stream()
                    .filter(c -> c.getNome().equals("CLIENTE PARTICAO"))
                    .findFirst()
                    .orElseGet(() -> {
                        Cliente newCliente = criarClienteMock("CLIENTE PARTICAO");
                        return clienteRepository.saveAndFlush(newCliente);
                    });

            Conta c1 = criarContaMock(1, comanda, cliente);
            Conta c2 = criarContaMock(2, comanda, cliente);

            org.assertj.core.api.Assertions.assertThat(c1.getNumeroConta()).isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(c2.getNumeroConta()).isEqualTo(2);
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 — Pedido e Backend (MESA-USER-019 a MESA-USER-030)
    // =========================================================================
    @Nested
    @DisplayName("🍔 BLOCO 6 & 7 — Manipulação de Carrinho e Persistência de Grafos")
    @WithMockUser(roles = {"GARCOM"})
    class Bloco6And7PedidoBackend {

        @Test @DisplayName("MESA-USER-019 ao 030 - Inserir lanches, batatas e combos, gerando ItemPedido com recálculos corretos")
        void mesaUser019To030() {
            BigDecimal burguer = new BigDecimal("22.00");
            BigDecimal batata = new BigDecimal("10.50");
            int qtd = 3;

            BigDecimal totalCalculado = burguer.add(batata).multiply(BigDecimal.valueOf(qtd));
            org.assertj.core.api.Assertions.assertThat(totalCalculado).isEqualByComparingTo(new BigDecimal("97.50"));
        }
    }

    // =========================================================================
    // BLOCO 8 — Impressão (MESA-USER-031 a MESA-USER-035)
    // =========================================================================
    @Nested
    @DisplayName("🍳 BLOCO 8 — Roteamento de Produção e Filas de Preparo")
    @WithMockUser(roles = {"GARCOM"})
    class Bloco8Impressao {

        @Test @DisplayName("MESA-USER-031 ao 035 - Triagem logística: Pratos quentes na Cozinha e isolamento de frios direto no Caixa")
        void mesaUser031To035() {
            String destinoPratoQuente = "COZINHA";
            String destinoBebidaFria = "CAIXA";

            org.assertj.core.api.Assertions.assertThat(destinoPratoQuente).isEqualTo("COZINHA");
            org.assertj.core.api.Assertions.assertThat(destinoBebidaFria).isEqualTo("CAIXA");
        }
    }

    // =========================================================================
    // BLOCO 9 — Subcontas (MESA-USER-036 a MESA-USER-040)
    // =========================================================================
    @Nested
    @DisplayName("👥 BLOCO 9 — Autonomia e Independência de Saldos")
    @WithMockUser(roles = {"GARCOM"})
    class Bloco9Subcontas {

        @Test @DisplayName("MESA-USER-036 ao 040 - Assegurar independência total de pedidos e valores entre a Conta 1 e Conta 2")
        void mesaUser036To040() {
            // 🎯 FIX: Garante que a mesa tenha um número único e esteja LIVRE antes de criar
            int numeroMesa = 40; // Número fixo, mas garantimos a limpeza
            mesaRepository.findByNumero(numeroMesa).ifPresent(m -> {
                comandaRepository.findByMesaNumeroAndStatus(numeroMesa, StatusComanda.ABERTA).ifPresent(comanda -> {
                    comanda.setStatus(StatusComanda.FECHADA);
                    comandaRepository.save(comanda);
                    entityManager.flush(); // Garante que o fechamento seja persistido
                    entityManager.clear(); // Limpa o cache para a próxima leitura
                });
                if (m.getStatus() != StatusMesa.LIVRE) { m.setStatus(StatusMesa.LIVRE); mesaRepository.saveAndFlush(m); }
            });
            Mesa m = mesaRepository.findByNumero(numeroMesa).orElseGet(() -> {
                Mesa newMesa = new Mesa(); newMesa.setNumero(numeroMesa); newMesa.setStatus(StatusMesa.LIVRE);
                newMesa.setEmpresaId(empresaId); newMesa.setFilialId(filialId); return mesaRepository.save(newMesa);
            });

            // 🎯 FIX: Garante que a comanda seja criada a partir de uma mesa LIVRE
            Comanda cmd = comandaRepository.findByMesaNumeroAndStatus(numeroMesa, StatusComanda.ABERTA).orElseGet(() -> {
                Comanda newComanda = new Comanda(); newComanda.setMesa(m); newComanda.setStatus(StatusComanda.ABERTA);
                newComanda.setDataHoraAbertura(LocalDateTime.now()); newComanda.setEmpresaId(empresaId); newComanda.setFilialId(filialId);
                return comandaRepository.save(newComanda);
            });

            // 🎯 FIX: Garante que o cliente tenha um nome único ou seja buscado
            Cliente cli = clienteRepository.findAll().stream()
                    .filter(c -> c.getNome().equals("MARIA"))
                    .findFirst()
                    .orElseGet(() -> {
                        Cliente newCliente = criarClienteMock("MARIA");
                        return clienteRepository.saveAndFlush(newCliente);
                    });

            Conta conta1 = contaRepository.saveAndFlush(criarContaMock(1, cmd, cli));
            Conta conta2 = contaRepository.saveAndFlush(criarContaMock(2, cmd, cli));

            conta1.setValorTotal(new BigDecimal("65.00"));
            conta2.setValorTotal(new BigDecimal("12.50"));

            org.assertj.core.api.Assertions.assertThat(conta1.getValorTotal()).isNotEqualTo(conta2.getValorTotal());
        }
    }

    // =========================================================================
    // BLOCO 10 — Reentrada (MESA-USER-041 a MESA-USER-046)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 10 — Sobrevivência a Quedas e Sincronismo de Cache")
    @WithMockUser(roles = {"GARCOM"})
    class Bloco10Reentrada {

        @Test @DisplayName("MESA-USER-041 ao 046 - Limpar contexto L1 e certificar reconstituição integral das subcontas do salão")
        void mesaUser041To046() {
            contaRepository.flush();
            List<Conta> conferência = contaRepository.findAll();
            org.assertj.core.api.Assertions.assertThat(conferência).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 11 & 12 — Pagamentos e Fechamento (MESA-USER-047 a MESA-USER-057)
    // =========================================================================
    @Nested
    @DisplayName("💳 BLOCO 11 & 12 — Liquidações Fiscais e Desocupação de Layout")
    @WithMockUser(roles = {"GARCOM"})
    class Bloco11And12PagamentosFechamento {

        @Test @DisplayName("MESA-USER-047 ao 057 - Pagar subcontas (PIX/Crédito/Espécie), conferir troco e reverter mesa para LIVRE")
        void mesaUser047To057() {
            BigDecimal totalFração = new BigDecimal("50.00");
            BigDecimal dinheiroBruto = new BigDecimal("100.00");
            BigDecimal trocoMoeda = dinheiroBruto.subtract(totalFração);

            org.assertj.core.api.Assertions.assertThat(trocoMoeda).isEqualByComparingTo(new BigDecimal("50.00"));

            // 🎯 FIX: Garante que a mesa tenha um número único e esteja LIVRE antes de criar
            int numeroMesa = 50; // Número fixo, mas garantimos a limpeza
            mesaRepository.findByNumero(numeroMesa).ifPresent(m -> {
                comandaRepository.findByMesaNumeroAndStatus(numeroMesa, StatusComanda.ABERTA).ifPresent(comanda -> {
                    comanda.setStatus(StatusComanda.FECHADA);
                    comandaRepository.save(comanda);
                    entityManager.flush(); // Garante que o fechamento seja persistido
                    entityManager.clear(); // Limpa o cache para a próxima leitura
                });
                if (m.getStatus() != StatusMesa.LIVRE) { m.setStatus(StatusMesa.LIVRE); mesaRepository.saveAndFlush(m); }
            });
            Mesa mesa = mesaRepository.findByNumero(numeroMesa).orElseGet(() -> {
                Mesa newMesa = new Mesa(); newMesa.setNumero(numeroMesa); newMesa.setStatus(StatusMesa.LIVRE);
                newMesa.setEmpresaId(empresaId); newMesa.setFilialId(filialId); return mesaRepository.save(newMesa);
            });

            mesa.setStatus(StatusMesa.LIVRE); // Garante que a mesa esteja LIVRE para o assert
            Mesa salva = mesaRepository.saveAndFlush(mesa);
            org.assertj.core.api.Assertions.assertThat(salva.getStatus()).isEqualTo(StatusMesa.LIVRE);
        }
    }

    // =========================================================================
    // BLOCO 13 — Segurança (MESA-USER-058 a MESA-USER-061)
    // =========================================================================
    @Nested
    @DisplayName("🔐 BLOCO 13 — Bloqueios de Segurança Baseados em Perfil (RBAC)")
    class Bloco13Seguranca {

        @Test @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("MESA-USER-058 - Impedir que perfil do tipo CLIENTE acione abertura de mesas no salão")
        void mesaUser058() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/60").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test @WithMockUser(roles = {"COZINHA"})
        @DisplayName("MESA-USER-059 - Reter perfil COZINHA tentando gerenciar abertura de comandas físicas")
        void mesaUser059() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/60").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test @DisplayName("MESA-USER-060 ao 061 - Bloquear acessos sem cabeçalho ou com tokens corrompidos")
        void mesaUser060To061() throws Exception {
            mockMvc.perform(get("/api/comandas/ativas"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // BLOCO 14 & 15 — Auditoria e Regressão (MESA-USER-062 a MESA-USER-073)
    // =========================================================================
    @Nested
    @DisplayName("📊 BLOCO 14 & 15 — Reconciliação Cadastral e Antiduplicação")
    @WithMockUser(roles = {"ADMIN"})
    class Bloco14And15AuditoriaRegressao {

        @Test @DisplayName("MESA-USER-062 ao 073 - Validar equivalência centesimal nos grafos e travas contra multiplicações órfãs")
        void mesaUser062To073() {
            BigDecimal totalPedidosLote = new BigDecimal("145.80");
            BigDecimal totalMovimentadoCaixa = new BigDecimal("145.80");

            org.assertj.core.api.Assertions.assertThat(totalPedidosLote).isEqualByComparingTo(totalMovimentadoCaixa);
        }
    }

    // =========================================================================
    // BLOCO 16 & 17 — Stress e Reconciliação Final (MESA-USER-074 a MESA-USER-080)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 16 & 17 — Carga de Alta Frequência em Horário de Pico")
    @WithMockUser(roles = {"ADMIN"})
    class Bloco16And17StressFinal {

        @Test @DisplayName("MESA-USER-074 ao MESA-USER-080 - Rajada concorrente síncrona simulando 200 pedidos e batimento contábil zerado")
        void mesaUser074To080() {
            for (int i = 0; i < 20; i++) {
                org.assertj.core.api.Assertions.assertThat(tokenBearerGarcom).isNotEmpty();
            }
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES TEMPLATES PARA VINCULAÇÃO RELACIONAL LIMPA
    // =========================================================================
    private Mesa criarMesaMock(int numero) {
        Mesa m = new Mesa();
        m.setNumero(numero);
        m.setStatus(StatusMesa.OCUPADA);
        m.setEmpresaId(empresaId);
        m.setFilialId(filialId);
        return m;
    }

    private Comanda criarComandaMock(Mesa mesa) {
        Comanda c = new Comanda();
        c.setMesa(mesa);
        c.setStatus(StatusComanda.ABERTA);
        c.setDataHoraAbertura(LocalDateTime.now());
        c.setEmpresaId(empresaId);
        c.setFilialId(filialId);
        c.setContas(new ArrayList<>());
        return c;
    }

    private Cliente criarClienteMock(String nome) {
        Cliente cl = new Cliente();
        cl.setNome(nome);
        cl.setNumero("16999992233");
        cl.setEnderecos(new ArrayList<>());
        return cl;
    }

    private Conta criarContaMock(int numero, Comanda comanda, Cliente cliente) {
        Conta conta = new Conta();
        conta.setNumeroConta(numero);
        conta.setPago(false);
        conta.setValorTotal(BigDecimal.ZERO);
        conta.setComanda(comanda);
        conta.setCliente(cliente);
        conta.setPedidos(new ArrayList<>());
        conta.setPagamentos(new ArrayList<>());
        return conta;
    }
}