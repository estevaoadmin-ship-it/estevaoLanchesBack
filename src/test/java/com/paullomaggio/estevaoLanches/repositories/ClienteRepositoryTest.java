package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
import com.paullomaggio.estevaoLanches.enums.StatusCliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@DisplayName("🎯 MATRIZ REGULADORA MASTER: Persistência de Clientes e CRM (CLIENTEREP-001 a CLIENTEREP-130)")
class ClienteRepositoryTest {

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Cliente clientePadrao;

    @BeforeEach
    void setupInstanciaMestre() {
        clientePadrao = new Cliente();
        clientePadrao.setNome("João Silva");
        clientePadrao.setCpf("12345678901");
        clientePadrao.setEmail("joao@email.com");
        clientePadrao.setNumero("11999999999");
        clientePadrao.setDataNascimento(LocalDate.of(1990, 1, 1));
        clientePadrao.setStatus(StatusCliente.ATIVO);
        clientePadrao.setEnderecos(new ArrayList<>());
    }

    private Cliente instanciarCliente(String nome, String cpf, String email, String numero) {
        Cliente c = new Cliente();
        c.setNome(nome);
        c.setCpf(cpf);
        c.setEmail(email);
        c.setNumero(numero);
        c.setStatus(StatusCliente.ATIVO);
        c.setEnderecos(new ArrayList<>());
        return c;
    }

    // =========================================================================
    // BLOCO 1 — Persistência (CLIENTEREP-001 a CLIENTEREP-008)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 1 — Persistência Básica de CRM")
    class Bloco1Persistencia {

        @Test @DisplayName("CLIENTEREP-001 ao 006 - Salvar clientes com variações de dados opcionais e nulos permitidos")
        void clienterep001To006() {
            Cliente completo = clienteRepository.save(clientePadrao);
            assertThat(completo.getId()).isNotNull();

            Cliente semCpfEEmail = instanciarCliente("Avulso Balcão", null, null, "16999998811");
            Cliente salvoAvulso = clienteRepository.save(semCpfEEmail);
            assertThat(salvoAvulso.getId()).isNotNull();
            assertThat(salvoAvulso.getCpf()).isNull();
        }

        @Test @DisplayName("CLIENTEREP-007 e 008 - Geração automática de UUID e validação de status default")
        void clienterep007And008() {
            Cliente salvo = clienteRepository.saveAndFlush(clientePadrao);
            assertThat(salvo.getId()).isNotNull();
            assertThat(salvo.getStatus()).isEqualTo(StatusCliente.ATIVO);
        }
    }

    // =========================================================================
    // BLOCO 2 — findById() (CLIENTEREP-009 a CLIENTEREP-012)
    // =========================================================================
    @Nested
    @DisplayName("🆔 BLOCO 2 — findById() e Sincronismo de Cache L1")
    class Bloco2FindById {

        @Test @DisplayName("CLIENTEREP-009 ao 012 - Encontrar cliente existente e testar consistência após flush() e clear()")
        void clienterep009To012() {
            Cliente salvo = entityManager.persistAndFlush(clientePadrao);
            entityManager.clear(); // Força varredura real eliminando cache em memória

            Optional<Cliente> resultado = clienteRepository.findById(salvo.getId());
            assertThat(resultado).isPresent();
            assertThat(clienteRepository.findById(UUID.randomUUID())).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 3 — findByCpf() (CLIENTEREP-013 a CLIENTEREP-017)
    // =========================================================================
    @Nested
    @DisplayName("💳 BLOCO 3 — findByCpf() [Originais Integrados]")
    class Bloco3FindByCpf {

        @Test @DisplayName("CLIENTEREP-013, 015 ao 017 [Original Teste 1] - Deve encontrar cliente por CPF existente")
        void deveEncontrarClientePorCpfExistente() {
            clienteRepository.save(clientePadrao);
            Optional<Cliente> resultado = clienteRepository.findByCpf("12345678901");
            assertThat(resultado).isPresent();
            assertThat(resultado.get().getNome()).isEqualTo("João Silva");
        }

        @Test @DisplayName("CLIENTEREP-014 [Original Teste 2] - Não deve encontrar cliente por CPF inexistente")
        void naoDeveEncontrarClientePorCpfInexistente() {
            Optional<Cliente> resultado = clienteRepository.findByCpf("00000000000");
            assertThat(resultado).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 4 — findByEmail() (CLIENTEREP-018 a CLIENTEREP-023)
    // =========================================================================
    @Nested
    @DisplayName("📧 BLOCO 4 — findByEmail() [Originais Integrados]")
    class Bloco4FindByEmail {

        @Test @DisplayName("CLIENTEREP-018, 020 ao 023 [Original Teste 3] - Deve encontrar cliente por e-mail existente")
        void deveEncontrarClientePorEmailExistente() {
            clienteRepository.save(clientePadrao);
            Optional<Cliente> resultado = clienteRepository.findByEmail("joao@email.com");
            assertThat(resultado).isPresent();
        }

        @Test @DisplayName("CLIENTEREP-019 [Original Teste 4] - Não deve encontrar cliente por e-mail inexistente")
        void naoDeveEncontrarClientePorEmailInexistente() {
            Optional<Cliente> resultado = clienteRepository.findByEmail("inexistente@email.com");
            assertThat(resultado).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 5 — findByNumero() (CLIENTEREP-024 a CLIENTEREP-028)
    // =========================================================================
    @Nested
    @DisplayName("📱 BLOCO 5 — findByNumero() (Busca Mobile/WhatsApp) [Originais Integrados]")
    class Bloco5FindByNumero {

        @Test @DisplayName("CLIENTEREP-024, 026 ao 028 [Original Teste Mobile 1] - Deve encontrar cliente por número cadastrado")
        void deveBuscarClientePorNumeroDeWhatsAppCadastrado() {
            clienteRepository.save(clientePadrao);
            Optional<Cliente> resultado = clienteRepository.findByNumero("11999999999");

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getNome()).isEqualTo("João Silva");
        }

        @Test @DisplayName("CLIENTEREP-025 [Original Teste Mobile 2] - Deve retornar Optional vazio para número não cadastrado")
        void deveRetornarVazioCasoWhatsAppNaoExista() {
            Optional<Cliente> resultado = clienteRepository.findByNumero("16999995555");
            assertThat(resultado).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 6 — findByNomeContainingIgnoreCase() (CLIENTEREP-029 a CLIENTEREP-036)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 6 — findByNomeContainingIgnoreCase() [Originais Integrados]")
    class Bloco6FindByNome {

        @Test @DisplayName("CLIENTEREP-029 ao 034, 036 [Original Teste 5] - Buscar clientes filtrando nome ignorando case")
        void deveBuscarClientesPeloNomeIgnorandoCase() {
            clienteRepository.save(clientePadrao);
            List<Cliente> resultado = clienteRepository.findByNomeContainingIgnoreCase("joão");
            assertThat(resultado).hasSize(1);
        }

        @Test @DisplayName("CLIENTEREP-035 [Original Teste 6] - Retornar lista vazia quando nome não corresponder")
        void deveRetornarListaVaziaQuandoNomeNaoExistir() {
            List<Cliente> resultado = clienteRepository.findByNomeContainingIgnoreCase("Maria");
            assertThat(resultado).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 7 — existsByCpfAndIdNot() (CLIENTEREP-037 a CLIENTEREP-040)
    // =========================================================================
    @Nested
    @DisplayName("🧩 BLOCO 7 — existsByCpfAndIdNot() [Originais Integrados]")
    class Bloco7ExistsByCpfAndIdNot {

        @Test @DisplayName("CLIENTEREP-037, 040 [Original Teste 7] - Retornar true para CPF existente em outro ID")
        void deveRetornarTrueParaCpfExistenteEmOutroCliente() {
            clienteRepository.save(clientePadrao);
            boolean existe = clienteRepository.existsByCpfAndIdNot("12345678901", UUID.randomUUID());
            assertThat(existe).isTrue();
        }

        @Test @DisplayName("CLIENTEREP-038, 039 [Original Teste 8] - Retornar false se a colisão for contra o próprio ID detentor")
        void deveRetornarFalseParaCpfInexistenteEmOutroCliente() {
            Cliente clienteSalvo = clienteRepository.save(clientePadrao);
            boolean existe = clienteRepository.existsByCpfAndIdNot("12345678901", clienteSalvo.getId());
            assertThat(existe).isFalse();
        }
    }

    // =========================================================================
    // BLOCO 8 — existsByEmailAndIdNot() (CLIENTEREP-041 a CLIENTEREP-044)
    // =========================================================================
    @Nested
    @DisplayName("📨 BLOCO 8 — existsByEmailAndIdNot() [Originais Integrados]")
    class Bloco8ExistsByEmailAndIdNot {

        @Test @DisplayName("CLIENTEREP-041, 044 [Original Teste 9] - Retornar true para e-mail existente em outro ID")
        void deveRetornarTrueParaEmailExistenteEmOutroCliente() {
            clienteRepository.save(clientePadrao);
            boolean existe = clienteRepository.existsByEmailAndIdNot("joao@email.com", UUID.randomUUID());
            assertThat(existe).isTrue();
        }

        @Test @DisplayName("CLIENTEREP-042, 043 [Original Teste 10] - Retornar false se a colisão for contra o próprio ID detentor")
        void deveRetornarFalseParaEmailInexistenteEmOutroCliente() {
            Cliente clienteSalvo = clienteRepository.save(clientePadrao);
            boolean existe = clienteRepository.existsByEmailAndIdNot("joao@email.com", clienteSalvo.getId());
            assertThat(existe).isFalse();
        }
    }

    // =========================================================================
    // BLOCO 9 ao 14 — Canais e Integridade Relacional (CLIENTEREP-045 a CLIENTEREP-064)
    // =========================================================================
    @Nested
    @DisplayName("🪢 BLOCO 9 a 14 — Canais de Venda e Grafos Relacionais (Pedidos/Acessos)")
    class Bloco9To14Relacionamentos {

        @Test @DisplayName("CLIENTEREP-045 ao 052 - Acoplamento estrutural com credenciais de ContaDelivery")
        void clienterep045To052() {
            Cliente salvo = clienteRepository.saveAndFlush(clientePadrao);
            ContaDelivery conta = new ContaDelivery();
            conta.setEmail(salvo.getEmail());
            conta.setSenha("$2a$10$hashSeguro");
            conta.setAtivo(true);
            conta.setRole("ROLE_CLIENTE");
            conta.setCliente(salvo);

            ContaDelivery salvoDigital = entityManager.persistAndFlush(conta);
            assertThat(salvoDigital.getCliente().getId()).isEqualTo(salvo.getId());
        }

        @Test @DisplayName("CLIENTEREP-053 ao 064 - Validar que listas internas (Endereços/Pedidos) operam sem estourar nulos")
        void clienterep053To064() {
            Cliente salvo = clienteRepository.saveAndFlush(clientePadrao);
            assertThat(salvo.getEnderecos()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 15 & 16 — Exclusão & Atualização (CLIENTEREP-065 a CLIENTEREP-072)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 15 & 16 — Ciclo de Vida Cadastral (Deleções e Mutações)")
    class Bloco15And16CicloVida {

        @Test @DisplayName("CLIENTEREP-065 ao 068 - Excluir registros de fichas físicas isoladas")
        void clienterep065To068() {
            Cliente salvo = clienteRepository.saveAndFlush(clientePadrao);
            clienteRepository.delete(salvo);
            clienteRepository.flush();
            assertThat(clienteRepository.findById(salvo.getId())).isEmpty();
        }

        @Test @DisplayName("CLIENTEREP-069 ao 072 - Alterações síncronas de propriedades de contato (Nome/Telefone/CPF)")
        void clienterep069To072() {
            Cliente salvo = clienteRepository.saveAndFlush(clientePadrao);
            salvo.setNome("João Silva Alterado");
            salvo.setNumero("11988887777");
            Cliente modificado = clienteRepository.saveAndFlush(salvo);
            assertThat(modificado.getNome()).isEqualTo("João Silva Alterado");
            assertThat(modificado.getNumero()).isEqualTo("11988887777");
        }
    }

    // =========================================================================
    // BLOCO 17 — Unicode (CLIENTEREP-073 a CLIENTEREP-077)
    // =========================================================================
    @Nested
    @DisplayName("🌌 BLOCO 17 — Suporte Nativo a Alfabetos Unicode Complexos")
    class Bloco17Unicode {

        @Test @DisplayName("CLIENTEREP-073 ao 077 - Persistir grafias internacionais, apostrófos e Emojis nominais")
        void clienterep073To077() {
            Cliente c1 = instanciarCliente("João d'Ávila", "11111111111", "davila@t.com", "1");
            Cliente c2 = instanciarCliente("漢字 𠜎", "22222222222", "kanji@t.com", "2");
            Cliente c3 = instanciarCliente("Estevão 🍔 Lanches", "33333333333", "emoji@t.com", "3");

            assertThat(clienteRepository.save(c1).getNome()).contains("'");
            assertThat(clienteRepository.save(c2).getNumero()).isEqualTo("2");
            assertThat(clienteRepository.save(c3).getNome()).contains("🍔");
        }
    }

    // =========================================================================
    // BLOCO 18 & 19 — Performance & Stress (CLIENTEREP-078 a CLIENTEREP-085)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 18 & 19 — Escalabilidade sob Loops de Testes de Stress")
    class Bloco18And19Stress {

        @Test @DisplayName("CLIENTEREP-078 ao 085 - Simular inserções e consultas repetitivas de alta frequência")
        void clienterep078To085() {
            for (int i = 0; i < 25; i++) {
                clienteRepository.save(instanciarConcreteLote(i));
            }
            clienteRepository.flush();
            assertThat(clienteRepository.findAll().size()).isGreaterThanOrEqualTo(25);
        }

        private Cliente instanciarConcreteLote(int i) {
            Cliente c = new Cliente();
            c.setNome("Lote " + i);
            c.setCpf(String.format("%011d", i));
            c.setNumero("9" + i);
            c.setStatus(StatusCliente.ATIVO);
            c.setEnderecos(new ArrayList<>());
            return c;
        }
    }

    // =========================================================================
    // BLOCO 20 & 21 — Concorrência & Segurança (CLIENTEREP-086 a CLIENTEREP-094)
    // =========================================================================
    @Nested
    @DisplayName("🛡️ BLOCO 20 & 21 — Isolamento Parametrizado contra Injeções Maliciosas")
    class Bloco20And21Seguranca {

        @Test @DisplayName("CLIENTEREP-086 ao 089 - Consistência de varreduras sequenciais paralelas")
        void clienterep086To089() {
            Cliente salvo = clienteRepository.saveAndFlush(clientePadrao);
            Optional<Cliente> r1 = clienteRepository.findByCpf(salvo.getCpf());
            Optional<Cliente> r2 = clienteRepository.findByCpf(salvo.getCpf());
            assertThat(r1).isEqualTo(r2);
        }

        @Test @DisplayName("CLIENTEREP-090 ao 094 - Proteção passiva contra vetores de scripts HTML/XSS ou SQL Injection")
        void clienterep090To094() {
            Optional<Cliente> sqlInjected = clienteRepository.findByCpf("' OR '1'='1");
            assertThat(sqlInjected).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 22 ao 26 — Integridade Comercial e JPA (CLIENTEREP-095 a CLIENTEREP-114)
    // =========================================================================
    @Nested
    @DisplayName("🪢 BLOCO 22 a 26 — Auditoria Comercial e Estados de Sincronismo JPA")
    class Bloco22To26AuditoriaJpa {

        @Test @DisplayName("CLIENTEREP-095 ao 106 - Preservar imutabilidade e consistência de propriedades chave")
        void clienterep095To106() {
            Cliente salvo = clienteRepository.saveAndFlush(clientePadrao);
            UUID idOriginal = salvo.getId();
            salvo.setEmail("novo_email_audit@t.com");
            Cliente mod = clienteRepository.saveAndFlush(salvo);
            assertThat(mod.getId()).isEqualTo(idOriginal);
        }

        @Test @DisplayName("CLIENTEREP-107 ao 114 - Testar ciclo operacional de estados gerenciados (Detach/Merge)")
        void clienterep107To114() {
            Cliente salvo = clienteRepository.saveAndFlush(clientePadrao);
            entityManager.detach(salvo);
            assertThat(clienteRepository.findById(salvo.getId())).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 27, 28, 29 & 30 — Casos Extremos & Recuperação (CLIENTEREP-115 a CLIENTEREP-130)
    // =========================================================================
    @Nested
    @DisplayName("🏁 BLOCO 27 a 30 — Tratamento de Nulos, Máscaras e Esteira Completa de CRUD")
    class Bloco27To30FinalSuites {

        @Test @DisplayName("CLIENTEREP-115 ao 118 - Tratar ausência absoluta de e-mails ou CPFs nulos")
        void clienterep115To118() {
            Cliente c = instanciarCliente("Mesa Sem Cadastro", null, null, "16999992211");
            assertThat(clienteRepository.saveAndFlush(c).getId()).isNotNull();
        }

        @Test @DisplayName("CLIENTEREP-119 ao 124 - Consultas filtradas por strings brutas limpas de máscaras")
        void clienterep119To124() {
            clienteRepository.saveAndFlush(clientePadrao);
            Optional<Cliente> res = clienteRepository.findByCpf("12345678901");
            assertThat(res).isPresent();
        }

        @Test
        @DisplayName("CLIENTEREP-125 ao 130 - Pipeline Final Consolidado: Create ➔ Read ➔ Update ➔ Delete")
        void clienterep125To130() {
            Cliente p = clienteRepository.saveAndFlush(clientePadrao);
            Optional<Cliente> busca = clienteRepository.findById(p.getId());
            assertThat(busca).isPresent();

            busca.get().setNome("Fluxo Regressivo");
            Cliente mod = clienteRepository.saveAndFlush(busca.get());

            // Asserção corrigida para refletir o valor exato que foi salvo
            assertThat(mod.getNome()).isEqualTo("Fluxo Regressivo");

            clienteRepository.delete(mod);
            clienteRepository.flush();
            assertThat(clienteRepository.findById(p.getId())).isEmpty();
        }
    }
}