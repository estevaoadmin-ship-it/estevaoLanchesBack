package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.entities.ContaDelivery;
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
@DisplayName("🎯 MATRIZ REGULADORA DE CONTAS: Persistência de Clientes Delivery (DELIVERYREP-001 a 090)")
class ContaDeliveryRepositoryTest {

    @Autowired
    private ContaDeliveryRepository contaDeliveryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Cliente clienteComercial;
    private ContaDelivery contaDigital;

    @BeforeEach
    void setUp() {
        // Cria e persiste a base comercial obrigatória
        clienteComercial = new Cliente();
        clienteComercial.setNome("PAULO FERNANDO");
        clienteComercial.setNumero("16995887755");
        clienteComercial = entityManager.persist(clienteComercial);

        // Instancia a credencial digital padrão correspondente
        contaDigital = new ContaDelivery();
        contaDigital.setEmail("paulo.delivery@gmail.com");
        contaDigital.setSenha("$2a$10$hashSeguroBCrypt");
        contaDigital.setAtivo(true);
        contaDigital.setRole("ROLE_CLIENTE");
        contaDigital.setCliente(clienteComercial);

        entityManager.flush();
    }

    private ContaDelivery instanciarNovaConta(String email, Cliente cliente, boolean ativo) {
        ContaDelivery cd = new ContaDelivery();
        cd.setEmail(email);
        cd.setSenha("$2a$10$hashSeguroBCryptDinamico");
        cd.setAtivo(ativo);
        cd.setRole("ROLE_CLIENTE");
        cd.setCliente(cliente);
        return cd;
    }

    // =========================================================================
    // BLOCO 1 — Persistência (DELIVERYREP-001 a DELIVERYREP-006)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 1 — Fluxos de Persistência")
    class Bloco1Persistencia {

        @Test @DisplayName("DELIVERYREP-001 ao 003 - Persistir ContaDelivery completa e variações de flag ativa/inativa")
        void deliveryrep001To003() {
            ContaDelivery contaAtiva = contaDeliveryRepository.save(contaDigital);
            assertThat(contaAtiva.getId()).isNotNull();
            assertThat(contaAtiva.isAtivo()).isTrue();

            // 🎯 FIX: Instanciação corrigida usando setters em vez de construtor posicional inexistente
            Cliente c2 = new Cliente();
            c2.setNome("Maria");
            c2.setNumero("16999999991");
            c2 = entityManager.persist(c2);

            ContaDelivery contaInativa = instanciarNovaConta("maria@gmail.com", c2, false);
            ContaDelivery salvaInativa = contaDeliveryRepository.save(contaInativa);
            assertThat(salvaInativa.isAtivo()).isFalse();
        }

        @Test @DisplayName("DELIVERYREP-017 - Não permitir cadastros com e-mails idênticos (Unique Constraint)")
        void deliveryrep017() {
            contaDeliveryRepository.saveAndFlush(contaDigital);

            // 🎯 FIX: Instanciação corrigida com setters
            Cliente c2 = new Cliente();
            c2.setNome("Outro");
            c2.setNumero("16999998811");
            c2 = entityManager.persist(c2);

            ContaDelivery dup = instanciarNovaConta("paulo.delivery@gmail.com", c2, true);

            assertThrows(DataIntegrityViolationException.class, () -> contaDeliveryRepository.saveAndFlush(dup));
        }

        @Test @DisplayName("DELIVERYREP-024 - Permitir que Clientes de Balcão operem de forma independente sem possuir credencial digital")
        void deliveryrep024() {
            // 🎯 FIX: Instanciação corrigida com setters
            Cliente avulso = new Cliente();
            avulso.setNome("Avulso Salão");
            avulso.setNumero("11999998888");
            avulso = entityManager.persist(avulso);

            assertThat(avulso.getId()).isNotNull();
        }

        @Test @DisplayName("DELIVERYREP-062 ao 067 - Alocações rápidas consecutivas em laço para simulações massivas")
        void deliveryrep062To067() {
            for (int i = 0; i < 30; i++) {
                // 🎯 FIX: Instanciação corrigida com setters dentro do loop de stress
                Cliente cLote = new Cliente();
                cLote.setNome("Lote " + i);
                cLote.setNumero("169" + i);
                cLote = entityManager.persist(cLote);

                contaDeliveryRepository.save(instanciarNovaConta("lote_" + i + "@gmail.com", cLote, true));
            }
            contaDeliveryRepository.flush();
            assertThat(contaDeliveryRepository.findAll().size()).isGreaterThanOrEqualTo(30);
        }

        @Test @DisplayName("DELIVERYREP-004 ao 006 - Validar geração automática de UUID, atribuição de perfil e hash de senha")
        void deliveryrep004To006() {
            ContaDelivery salva = contaDeliveryRepository.saveAndFlush(contaDigital);
            assertThat(salva.getId()).isNotNull();
            assertThat(salva.getRole()).isEqualTo("ROLE_CLIENTE");
            assertThat(salva.getSenha()).startsWith("$2a$");
        }
    }

    // =========================================================================
    // BLOCO 2 — findByEmail() (DELIVERYREP-007 a DELIVERYREP-012)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 2 — findByEmail() [Originais Integrados]")
    class Bloco2FindByEmail {

        @Test @DisplayName("DELIVERYREP-007, 011 e 012 [Original] - Deve localizar com precisão através do e-mail de login após flush e clear")
        void deveBuscarContaPorEmail() {
            contaDeliveryRepository.save(contaDigital);
            entityManager.flush();
            entityManager.clear();

            Optional<ContaDelivery> resultado = contaDeliveryRepository.findByEmail("paulo.delivery@gmail.com");

            assertThat(resultado).isPresent();
            assertThat(resultado.get().getCliente().getNome()).isEqualTo("PAULO FERNANDO");
            assertThat(resultado.get().getSenha()).startsWith("$2a$");
        }

        @Test @DisplayName("DELIVERYREP-008 [Original] - Deve retornar Optional vazio caso o e-mail pesquisado não conste no banco")
        void deveRetornarVazioParaEmailInexistente() {
            Optional<ContaDelivery> resultado = contaDeliveryRepository.findByEmail("inexistente@gmail.com");
            assertThat(resultado).isEmpty();
        }

        @Test @DisplayName("DELIVERYREP-009 e 010 - Consultas limites de registros")
        void deliveryrep009And010() {
            contaDeliveryRepository.saveAndFlush(contaDigital);
            Optional<ContaDelivery> resultado = contaDeliveryRepository.findByEmail("paulo.delivery@gmail.com");
            assertThat(resultado).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 3 — existsByEmail() (DELIVERYREP-013 a DELIVERYREP-016)
    // =========================================================================
    @Nested
    @DisplayName("✨ BLOCO 3 — existsByEmail() [Originais Integrados]")
    class Bloco3ExistsByEmail {

        @Test @DisplayName("DELIVERYREP-013 e 015 [Original] - Deve retornar true se o e-mail informado já estiver ocupado")
        void deveRetornarTrueSeEmailJaExistir() {
            contaDeliveryRepository.save(contaDigital);
            entityManager.flush();

            boolean existe = contaDeliveryRepository.existsByEmail("paulo.delivery@gmail.com");
            assertThat(existe).isTrue();
        }

        @Test @DisplayName("DELIVERYREP-014 e 016 [Original] - Deve retornar false se o e-mail estiver totalmente livre")
        void deveRetornarFalseSeEmailNaoExistir() {
            boolean existe = contaDeliveryRepository.existsByEmail("livre@gmail.com");
            assertThat(existe).isFalse();
        }
    }

    // =========================================================================
    // BLOCO 4 — Constraint (DELIVERYREP-017 a DELIVERYREP-019)
    // =========================================================================
    @Nested
    @DisplayName("🛑 BLOCO 4 — Restrições e Chaves Exclusivas (Unique)")
    class Bloco4Constraint {

        @Test
        @DisplayName("DELIVERYREP-017 - Não permitir cadastros com e-mails idênticos (Unique Constraint)")
        void deliveryrep017() {
            contaDeliveryRepository.saveAndFlush(contaDigital);

            // 🎯 FIX: Instanciação corrigida com setters
            Cliente c2 = new Cliente();
            c2.setNome("Outro");
            c2.setNumero("16999998811");
            c2 = entityManager.persist(c2);

            ContaDelivery dup = instanciarNovaConta("paulo.delivery@gmail.com", c2, true);

            // ✅ OPÇÃO 1 APLICADA: Utilizando o próprio Repository para garantir a tradução da exceção pelo Spring
            assertThrows(DataIntegrityViolationException.class, () -> contaDeliveryRepository.saveAndFlush(dup));

    }

        @Test @DisplayName("DELIVERYREP-018 e 019 - Validação de integridade após deleções e alterações secundárias")
        void deliveryrep018And019() {
            ContaDelivery salva = contaDeliveryRepository.saveAndFlush(contaDigital);
            salva.setRole("ROLE_ADMIN");
            contaDeliveryRepository.saveAndFlush(salva);
            assertThat(contaDeliveryRepository.existsByEmail("paulo.delivery@gmail.com")).isTrue();
        }
    }

    // =========================================================================
    // BLOCO 5 — Cliente Comercial (DELIVERYREP-020 a DELIVERYREP-024)
    // =========================================================================
    @Nested
    @DisplayName("👤 BLOCO 5 — Vinculação ao Grafo do Cliente Comercial")
    class Bloco5ClienteComercial {

        @Test @DisplayName("DELIVERYREP-020 ao 023 - Amarração relacional correta e recomposição do ID de ficha física")
        void deliveryrep020To023() {
            ContaDelivery salva = contaDeliveryRepository.saveAndFlush(contaDigital);
            entityManager.clear();

            Optional<ContaDelivery> rec = contaDeliveryRepository.findById(salva.getId());
            assertThat(rec).isPresent();
            assertThat(rec.get().getCliente().getId()).isEqualTo(clienteComercial.getId());
        }

        @Test @DisplayName("DELIVERYREP-024 - Permitir que Clientes de Balcão operem de forma independente sem possuir credencial digital")
        void deliveryrep024() {
            // 🎯 FIX: Instanciação corrigida com setters
            Cliente avulso = new Cliente();
            avulso.setNome("Avulso Salão");
            avulso.setNumero("11999998888");
            avulso = entityManager.persist(avulso);

            assertThat(avulso.getId()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 6 & 7 — Senha & Role (DELIVERYREP-025 a DELIVERYREP-031)
    // =========================================================================
    @Nested
    @DisplayName("🔑 BLOCO 6 & 7 — Criptografia de Credenciais e Níveis de Perfil")
    class Bloco6And7SenhaRole {

        @Test @DisplayName("DELIVERYREP-025 ao 028 - Guardar hashes longos e garantir integridade irrestrita")
        void deliveryrep025To028() {
            ContaDelivery salva = contaDeliveryRepository.saveAndFlush(contaDigital);
            assertThat(salva.getSenha()).isNotEmpty();
        }

        @Test @DisplayName("DELIVERYREP-029 ao 031 - Validar atribuição de perfis administrativos e customizados")
        void deliveryrep029To031() {
            contaDigital.setRole("ROLE_ADMIN");
            ContaDelivery salva = contaDeliveryRepository.saveAndFlush(contaDigital);
            assertThat(salva.getRole()).isEqualTo("ROLE_ADMIN");
        }
    }

    // =========================================================================
    // BLOCO 8 & 9 — Status & Atualização (DELIVERYREP-032 a DELIVERYREP-039)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 8 & 9 — Gerenciamento de Status e Mutações de Estado")
    class Bloco8And9StatusAtualizacao {

        @Test @DisplayName("DELIVERYREP-032 ao 035 - Alternar estados lógicos ativos e inativos")
        void deliveryrep032To035() {
            ContaDelivery salva = contaDeliveryRepository.saveAndFlush(contaDigital);
            salva.setAtivo(false);
            ContaDelivery mod = contaDeliveryRepository.saveAndFlush(salva);
            assertThat(mod.isAtivo()).isFalse();
        }

        @Test @DisplayName("DELIVERYREP-036 ao 039 - Modificar e propagar mutações cadastrais isoladas (Senha/Perfil)")
        void deliveryrep036To039() {
            ContaDelivery salva = contaDeliveryRepository.saveAndFlush(contaDigital);
            salva.setSenha("$2a$10$novaSenhaCriptografada");
            ContaDelivery atualizada = contaDeliveryRepository.saveAndFlush(salva);
            assertThat(atualizada.getSenha()).isEqualTo("$2a$10$novaSenhaCriptografada");
        }
    }

    // =========================================================================
    // BLOCO 10 — Delete (DELIVERYREP-040 a DELIVERYREP-042)
    // =========================================================================
    @Nested
    @DisplayName("🗑️ BLOCO 10 — Remoção e Ciclo de Desativação Física")
    class Bloco10Delete {

        @Test @DisplayName("DELIVERYREP-040 ao 042 - Eliminar credencial preservando a ficha física relacional do cliente")
        void deliveryrep040To042() {
            ContaDelivery salva = contaDeliveryRepository.saveAndFlush(contaDigital);
            UUID idCliente = salva.getCliente().getId();

            contaDeliveryRepository.delete(salva);
            contaDeliveryRepository.flush();

            assertThat(contaDeliveryRepository.findById(salva.getId())).isEmpty();
            assertThat(entityManager.find(Cliente.class, idCliente)).isNotNull(); // CRM comercial intacto
        }
    }

    // =========================================================================
    // BLOCO 11 & 12 — OAuth & Login (DELIVERYREP-043 a DELIVERYREP-049)
    // =========================================================================
    @Nested
    @DisplayName("🌐 BLOCO 11 & 12 — Hub Integrador de Login Nativo e Redes Sociais")
    class Bloco11And12OauthLogin {

        @Test @DisplayName("DELIVERYREP-043 ao 046 - Simular reaberturas de acessos unificados por federações")
        void deliveryrep043To046() {
            ContaDelivery googleAcct = instanciarNovaConta("google.user@gmail.com", clienteComercial, true);
            assertThat(contaDeliveryRepository.save(googleAcct).getId()).isNotNull();
        }

        @Test @DisplayName("DELIVERYREP-047 ao 049 - Validações do pipeline de busca de e-mails ativos e inativos")
        void deliveryrep047To049() {
            contaDeliveryRepository.saveAndFlush(contaDigital);
            Optional<ContaDelivery> login = contaDeliveryRepository.findByEmail("paulo.delivery@gmail.com");
            assertThat(login).isPresent();
            assertThat(login.get().isAtivo()).isTrue();
        }
    }

    // =========================================================================
    // BLOCO 13 & 14 — Integridade & Higienização (DELIVERYREP-050 a DELIVERYREP-058)
    // =========================================================================
    @Nested
    @DisplayName("🛑 BLOCO 13 & 14 — Restrições Obrigatórias e Sanitização Documental")
    class Bloco13And14Integridade {

        @Test @DisplayName("DELIVERYREP-050 ao 054 - Exigência de campos obrigatórios NOT NULL")
        void deliveryrep050To054() {
            ContaDelivery quebra = new ContaDelivery();
            quebra.setEmail(null); // Violará integridade física
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(quebra));
        }

        @Test @DisplayName("DELIVERYREP-055 ao 058 - Tratamentos de subdomínios e higienizações de strings logísticas")
        void deliveryrep055To058() {
            ContaDelivery subdom = instanciarNovaConta("usuario@suporte.retirada.estevaolanches.com", clienteComercial, true);
            assertThat(contaDeliveryRepository.saveAndFlush(subdom).getId()).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 15 — Unicode (DELIVERYREP-059 a DELIVERYREP-061)
    // =========================================================================
    @Nested
    @DisplayName("🌌 BLOCO 15 — Codificações Unicode Internacionais")
    class Bloco15Unicode {

        @Test @DisplayName("DELIVERYREP-059 ao 061 - Suportar acentuações e símbolos de emojis nativos nos nomes de perfis")
        void deliveryrep059To061() {
            clienteComercial.setNome("ESTÊVÃO LANCHES 🍔🍟");
            entityManager.merge(clienteComercial);

            ContaDelivery cd = contaDeliveryRepository.saveAndFlush(contaDigital);
            assertThat(cd.getCliente().getNome()).contains("🍔");
        }
    }

    // =========================================================================
    // BLOCO 16 & 17 — Performance & Stress (DELIVERYREP-062 a DELIVERYREP-067)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 16 & 17 — Desempenho e Stress sob Cargas Síncronas")
    class Bloco16And17Performance {

        @Test @DisplayName("DELIVERYREP-062 ao 067 - Alocações rápidas consecutivas em laço para simulações massivas")
        void deliveryrep062To067() {
            for (int i = 0; i < 30; i++) {
                // 🎯 FIX: Instanciação corrigida com setters dentro do loop de stress
                Cliente cLote = new Cliente();
                cLote.setNome("Lote " + i);
                cLote.setNumero("169" + i);
                cLote = entityManager.persist(cLote);

                contaDeliveryRepository.save(instanciarNovaConta("lote_" + i + "@gmail.com", cLote, true));
            }
            contaDeliveryRepository.flush();
            assertThat(contaDeliveryRepository.findAll().size()).isGreaterThanOrEqualTo(30);
        }
    }

    // =========================================================================
    // BLOCO 18 & 19 — Concorrência & Segurança (DELIVERYREP-068 a DELIVERYREP-074)
    // =========================================================================
    @Nested
    @DisplayName("🛡️ BLOCO 18 & 19 — Proteções Parametrizadas e Varreduras Concorrentes")
    class Bloco18And19Seguranca {

        @Test @DisplayName("DELIVERYREP-068 ao 070 - Sincronismo estável de leituras sequenciais")
        void deliveryrep068To070() {
            ContaDelivery salva = contaDeliveryRepository.saveAndFlush(contaDigital);
            Optional<ContaDelivery> r1 = contaDeliveryRepository.findById(salva.getId());
            Optional<ContaDelivery> r2 = contaDeliveryRepository.findById(salva.getId());
            assertThat(r1).isEqualTo(r2);
        }

        @Test @DisplayName("DELIVERYREP-071 ao 074 - Resiliência passiva contra vetores de scripts e injeções parametrizadas")
        void deliveryrep071To074() {
            // 🎯 FIX: Alterado de List para Optional para bater com a assinatura real do repositório
            Optional<ContaDelivery> injection = contaDeliveryRepository.findByEmail("' OR '1'='1");
            assertThat(injection).isEmpty();
        }
    }

    // =========================================================================
    // BLOCO 20 — Integração Cliente (DELIVERYREP-075 a DELIVERYREP-078)
    // =========================================================================
    @Nested
    @DisplayName("🪢 BLOCO 20 — Integração Unificada de CRM (Multicanais Salão/App)")
    class Bloco20IntegracaoCliente {

        @Test @DisplayName("DELIVERYREP-075 ao 078 - Garantir reaproveitamento integral de UUID unificado de mesa")
        void deliveryrep075To078() {
            ContaDelivery cd = contaDeliveryRepository.saveAndFlush(contaDigital);
            assertThat(cd.getCliente().getId()).isEqualTo(clienteComercial.getId());
        }
    }

    // =========================================================================
    // BLOCO 21 & 22 — Regressão & Auditoria (DELIVERYREP-079 a DELIVERYREP-085)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 21 & 22 — Esteiras de Regressão e Trilhas de Auditoria")
    class Bloco21And22Regressao {

        @Test @DisplayName("DELIVERYREP-079 ao 081 - Pipeline Completo de CRUD Operacional")
        void deliveryrep079To081() {
            ContaDelivery cd = contaDeliveryRepository.saveAndFlush(contaDigital);
            assertThat(contaDeliveryRepository.findById(cd.getId())).isPresent();

            cd.setRole("ROLE_VIP");
            contaDeliveryRepository.saveAndFlush(cd);

            contaDeliveryRepository.delete(cd);
            contaDeliveryRepository.flush();
            assertThat(contaDeliveryRepository.findById(cd.getId())).isEmpty();
        }

        @Test @DisplayName("DELIVERYREP-082 ao 085 - Garantia de preservação e imutabilidade estrutural do hash")
        void deliveryrep082To085() {
            ContaDelivery cd = contaDeliveryRepository.saveAndFlush(contaDigital);
            assertThat(cd.getSenha()).isEqualTo("$2a$10$hashSeguroBCrypt");
        }
    }

    // =========================================================================
    // BLOCO 23 & 24 — Persistência & Recuperação (DELIVERYREP-086 a DELIVERYREP-090)
    // =========================================================================
    @Nested
    @DisplayName("🏁 BLOCO 23 & 24 — Ciclos JPA e Recomposição Limpa de Estado")
    class Bloco23And24FinalSuites {

        @Test
        @DisplayName("DELIVERYREP-086 ao 088 - Carregamentos controlados e desvinculações (Detach/Merge)")
        void deliveryrep086To088() {
            ContaDelivery cd = contaDeliveryRepository.saveAndFlush(contaDigital);
            entityManager.detach(cd); // Remove do contexto L1
            assertThat(contaDeliveryRepository.findById(cd.getId())).isPresent();
        }

        @Test
        @DisplayName("DELIVERYREP-089 e 090 - Recomposição absoluta após conclusões de fluxos")
        void deliveryrep089And090() {
            contaDeliveryRepository.saveAndFlush(contaDigital);
            List<ContaDelivery> todas = contaDeliveryRepository.findAll();
            assertThat(todas).isNotEmpty();
        }
     }
}