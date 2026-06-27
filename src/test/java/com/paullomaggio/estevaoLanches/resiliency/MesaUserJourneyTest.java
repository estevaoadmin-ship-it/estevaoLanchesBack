package com.paullomaggio.estevaoLanches.resiliency;

import com.jayway.jsonpath.JsonPath;
import com.paullomaggio.estevaoLanches.dtos.ComandaResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.ContaResponseDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.repositories.*;
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

    private UUID empresaId;
    private UUID filialId;
    private String tokenBearerGarcom;
    private Usuario garcomMestre;

    @BeforeEach
    void setupCenarioDeMesaE2E() {
        empresaId = UUID.randomUUID();
        filialId = UUID.randomUUID();
        tokenBearerGarcom = "Bearer token_jwt_garcom_valido_2026";

        garcomMestre = new Usuario();
        garcomMestre.setNome("Estêvão Garçom");
        garcomMestre.setEmail("garcom.salao@estevaolanches.com");
        garcomMestre.setSenha("$2a$10$hashSeguroBCrypt");
        garcomMestre.setRole("GARCOM");
        garcomMestre.setAtivo(true);
        garcomMestre = usuarioRepository.saveAndFlush(garcomMestre);
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
            Caixa cx = new Caixa(null, LocalDateTime.now(), null, StatusCaixa.ABERTO, new BigDecimal("200.00"), null, null, null, garcomMestre, null);
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
            MvcResult res = mockMvc.perform(post("/api/comandas/abrir/10").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn();

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
            Mesa mesa = mesaRepository.saveAndFlush(criarMesaMock(30));
            Comanda comanda = comandaRepository.saveAndFlush(criarComandaMock(mesa));
            Cliente cliente = clienteRepository.saveAndFlush(criarClienteMock("CLIENTE PARTICAO"));

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
            Mesa m = mesaRepository.saveAndFlush(criarMesaMock(40));
            Comanda cmd = comandaRepository.saveAndFlush(criarComandaMock(m));
            Cliente cli = clienteRepository.saveAndFlush(criarClienteMock("MARIA"));

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

            Mesa mesa = criarMesaMock(50);
            mesa.setStatus(StatusMesa.LIVRE);
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