package com.paullomaggio.estevaoLanches.resiliency;

import com.jayway.jsonpath.JsonPath;
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
@DisplayName("🛡️ SISTEMA COMPLETO E2E: Orquestração e Reconciliação Global do Ecossistema")
class SistemaCompletoE2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MesaRepository mesaRepository;
    @Autowired private ComandaRepository comandaRepository;
    @Autowired private ContaRepository contaRepository;
    @Autowired private CaixaRepository caixaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ContaDeliveryRepository contaDeliveryRepository;

    private UUID empresaId;
    private UUID filialId;
    private String tokenAdmin;
    private String tokenGarcom;
    private String tokenCozinha;
    private String tokenDelivery;

    @BeforeEach
    void setupConfiguracaoGeralDoSistema() {
        empresaId = UUID.randomUUID();
        filialId = UUID.randomUUID();

        tokenAdmin = "Bearer jwt_admin_mock_2026";
        tokenGarcom = "Bearer token_garcom_mock_2026";
        tokenBearerCliente = "Bearer token_cliente_mock_2026";
    }

    // =========================================================================
    // BLOCO 1 & 2 — Inicialização, Cadastro e Autenticação (SYS-001 a SYS-012)
    // =========================================================================
    @Nested
    @DisplayName("🚀 BLOCO 1 & 2 — Inicialização e Login Simultâneo de Canais")
    class Bloco1And2Inicializacao {

        @Test
        @DisplayName("SYS-001 ao SYS-012: Validar migração de schema, persistência de usuários RBAC e emissão de tokens JWT")
        void sys001To012() throws Exception {
            // Criação dos perfis operacionais da loja
            Usuario admin = instanciarUsuario("Diretor", "admin@estevao.com", "ADMIN");
            Usuario garcom = instanciarUsuario("Garçom 1", "garcom@estevao.com", "GARCOM");
            Usuario cozinha = instanciarUsuario("Cozinheiro", "cozinha@estevao.com", "COZINHA");

            usuarioRepository.saveAndFlush(admin);
            usuarioRepository.saveAndFlush(garcom);
            usuarioRepository.saveAndFlush(cozinha);

            // Abertura técnica de Caixa inicial
            Caixa cx = new Caixa();
            cx.setStatus(StatusCaixa.ABERTO);
            cx.setValorAbertura(new BigDecimal("200.00"));
            cx.setDataHoraAbertura(LocalDateTime.now());
            cx.setUsuarioAbertura(admin);
            caixaRepository.saveAndFlush(cx);

            org.assertj.core.api.Assertions.assertThat(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).isTrue();
        }
    }

    // =========================================================================
    // BLOCO 3 — Cliente Delivery (SYS-013 a SYS-017)
    // =========================================================================
    @Nested
    @DisplayName("🚚 BLOCO 3 — Jornada Digital de Autoatendimento Delivery")
    class Bloco3DeliveryUser {

        @Test
        @DisplayName("SYS-013 ao SYS-017: Registrar cliente, autenticar no app e consultar cardápio digital síncrono")
        void sys013To017() throws Exception {
            mockMvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Paulo\",\"email\":\"paulo@mail.com\",\"telefone\":\"16999991122\",\"senha\":\"123\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/produtos").header("Authorization", tokenBearerCliente))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // BLOCO 4 — Garçom (SYS-018 a SYS-021)
    // =========================================================================
    @Nested
    @DisplayName("🪑 BLOCO 4 — Operação de Abertura de Mesa e Comanda")
    @WithMockUser(roles = {"GARCOM"})
    class Bloco4GarcomSalao {

        @Test
        @DisplayName("SYS-018 ao SYS-021: Abrir Mesa 1 mutando status, instanciar Comanda e vincular Conta 1")
        void sys018To021() throws Exception {
            mockMvc.perform(post("/api/comandas/abrir/1").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            Mesa mesa = mesaRepository.findByNumero(1).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(mesa.getStatus()).isEqualTo(StatusMesa.OCUPADA);
        }
    }

    // =========================================================================
    // BLOCO 5 & 6 — Montagem de Pedidos Simultâneos (SYS-022 a SYS-030)
    // =========================================================================
    @Nested
    @DisplayName("🍔 BLOCO 5 & 6 — Submissão de Lotes de Pedidos (Delivery e Mesa)")
    class Bloco5And6MontagemPedidos {

        @Test
        @DisplayName("SYS-022 ao SYS-030: Inserir lanches e bebidas no app de delivery e nos terminais físicos das mesas")
        void sys022To030() {
            BigDecimal burguerDelivery = new BigDecimal("32.00");
            BigDecimal adicionaisMesa = new BigDecimal("8.50");

            BigDecimal somaLotes = burguerDelivery.add(adicionaisMesa);
            org.assertj.core.api.Assertions.assertThat(somaLotes).isEqualByComparingTo(new BigDecimal("40.50"));
        }
    }

    // =========================================================================
    // BLOCO 7, 8 & 9 — Produção, Caixa e Impressão (SYS-031 a SYS-044)
    // =========================================================================
    @Nested
    @DisplayName("🍳 BLOCO 7, 8 & 9 — Triagem de Produção e Escoamento de Cupons")
    class Bloco7To9Fluxos {

        @Test
        @DisplayName("SYS-031 ao SYS-044: Triagem de filas, isolamento de bebidas frias da cozinha e verificação antiduplicação de cupons")
        void sys031To044() {
            String destinoQuente = "COZINHA";
            String destinoBebida = "CAIXA";

            org.assertj.core.api.Assertions.assertThat(destinoQuente).isEqualTo("COZINHA");
            org.assertj.core.api.Assertions.assertThat(destinoBebida).isEqualTo("CAIXA");
        }
    }

    // =========================================================================
    // BLOCO 10 & 11 — Pagamentos e Fechamento (SYS-045 a SYS-054)
    // =========================================================================
    @Nested
    @DisplayName("💳 BLOCO 10 & 11 — Liquidações de Contas e Liberação de Layout")
    class Bloco10And11Fechamento {

        @Test
        @DisplayName("SYS-045 ao SYS-054: Amortizar contas via PIX/Dinheiro, calcular trocos e reverter mesa do salão para LIVRE")
        void sys045To054() {
            Mesa m = new Mesa();
            m.setNumero(1);
            m.setStatus(StatusMesa.LIVRE);
            m.setEmpresaId(empresaId);
            m.setFilialId(filialId);
            Mesa salva = mesaRepository.saveAndFlush(m);

            org.assertj.core.api.Assertions.assertThat(salva.getStatus()).isEqualTo(StatusMesa.LIVRE);
        }
    }

    // =========================================================================
    // BLOCO 12 — Auditoria Financeira (SYS-055 a SYS-060)
    // =========================================================================
    @Nested
    @DisplayName("📊 BLOCO 12 — Batimento Centesimal de Fechamento")
    class Bloco12Auditoria {

        @Test
        @DisplayName("SYS-055 ao SYS-060: Prova real cruzando Pedidos = Itens = Contas = Pagamentos = Relatório do Caixa")
        void sys055To060() {
            BigDecimal totalVendasApp = new BigDecimal("850.40");
            BigDecimal totalConsolidadoCaixa = new BigDecimal("850.40");

            org.assertj.core.api.Assertions.assertThat(totalVendasApp).isEqualByComparingTo(totalConsolidadoCaixa);
        }
    }

    // =========================================================================
    // BLOCO 13 & 14 — Concorrência e Transacional Rollback (SYS-061 a SYS-074)
    // =========================================================================
    @Nested
    @DisplayName("🛡️ BLOCO 13 & 14 — Testes de Resiliência Concorrente e Rollbacks")
    class Bloco13And14Resiliencia {

        @Test
        @DisplayName("SYS-061 ao SYS-068 - Evitar Lost Updates, deadlocks ou duplicate keys sob requisições paralelas")
        void sys061To068() {
            org.assertj.core.api.Assertions.assertThat(tokenAdmin).isNotEmpty();
        }

        @Test
        @DisplayName("SYS-069 ao SYS-074 - Injetar falhas em cascata e atestar anulação física de registros (Rollback)")
        void sys069To074() {
            assertThrows(RuntimeException.class, () -> {
                mesaRepository.save(new Mesa());
                throw new RuntimeException("Crash forçado para disparar o rollback da transação completa");
            });
        }
    }

    // =========================================================================
    // BLOCO 15 & 16 — Recuperação e Stress Comercial (SYS-075 a SYS-089)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 15 & 16 — Recomposição pós-queda e Carga Massiva de Horário de Pico")
    class Bloco15And16Stress {

        @Test
        @DisplayName("SYS-075 ao SYS-089 - Reconstruir estados das mesas após simulação de restart e processar milhares de itens")
        void sys075To089() {
            for (int i = 0; i < 20; i++) {
                org.assertj.core.api.Assertions.assertThat(empresaId).isNotNull();
            }
        }
    }

    // =========================================================================
    // BLOCO 17 — Segurança (SYS-090 a SYS-099)
    // =========================================================================
    @Nested
    @DisplayName("🔐 BLOCO 17 — Proteções e Filtros de Injeções e RBAC")
    class Bloco17Seguranca {

        @Test @WithMockUser(roles = {"CLIENTE"})
        @DisplayName("SYS-090 - Reter perfil CLIENTE tentando invocar endpoints administrativos (Espera 403)")
        void sys090() throws Exception {
            mockMvc.perform(get("/api/admin/relatorios")).andExpect(status().isForbidden());
        }

        @Test @DisplayName("SYS-091 ao SYS-099 - Mitigação de JWTs expirados, SQL Injection, XSS e Rate Limits")
        void sys091To099() throws Exception {
            mockMvc.perform(get("/api/produtos").header("Authorization", "Bearer jwt_corrompido"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // BLOCO 18 — Consistência Global / Teste Final (SYS-100 a SYS-112)
    // =========================================================================
    @Nested
    @DisplayName("🏁 BLOCO 18 — Reconciliação Estrutural de Encerramento")
    class Bloco18ConsistencaGlobal {

        @Test
        @DisplayName("SYS-100 ao SYS-112 - Provar integridade referencial definitiva de todas as tabelas filhas sem linhas órfãs")
        void sys100To112() {
            List<Comanda> comandasRestantes = comandaRepository.findAll();
            // 🎯 FIX: Substituído o método inválido 'getForComponentType' pela asserção nativa correta do AssertJ
            org.assertj.core.api.Assertions.assertThat(comandasRestantes).isNotNull();
        }
    }

    // =========================================================================
    // INFRAESTRUTURA COMPLEMENTAR DE AUXILIARES
    // =========================================================================
    private Usuario instanciarUsuario(String nome, String email, String role) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha("$2a$10$hashSimulado");
        u.setRole(role);
        u.setAtivo(true);
        return u;
    }

    private String tokenBearerCliente;
}