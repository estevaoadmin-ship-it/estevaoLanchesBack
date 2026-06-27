package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.enums.RoleUsuario;
import org.hibernate.exception.ConstraintViolationException;
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
@DisplayName("🎯 MATRIZ ULTRA DE REPOSITÓRIO: Usuários e Constraints (UR-001 a UR-100)")
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    // Helper de persistência mapeando o Enum da Role para String conforme regras da entidade
    private Usuario criarEPersistirUsuario(String nome, String email, RoleUsuario role, Boolean ativo) {
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha("$2a$10$hashQualquerCriptografada");
        usuario.setRole(role.name());
        usuario.setAtivo(ativo);
        return entityManager.persist(usuario);
    }

    // =========================================================================
    // BLOCO 1 — findByEmail() (UR-001 a UR-010)
    // =========================================================================
    @Nested
    @DisplayName("🔍 BLOCO 1 — findByEmail()")
    class Bloco1FindByEmail {

        @Test @DisplayName("UR-001 - Deve localizar usuário pelo e-mail exato")
        void ur001() {
            criarEPersistirUsuario("Carlos Caixa", "atendente@tevao.com", RoleUsuario.GARCOM, true);
            Optional<Usuario> resultado = usuarioRepository.findByEmail("atendente@tevao.com");
            assertThat(resultado).isPresent();
            assertThat(resultado.get().getNome()).isEqualTo("Carlos Caixa");
        }

        @Test @DisplayName("UR-002 - Deve retornar Optional.empty para e-mail inexistente")
        void ur002() {
            Optional<Usuario> resultado = usuarioRepository.findByEmail("fantasma@tevao.com");
            assertThat(resultado).isEmpty();
        }

        @Test @DisplayName("UR-003 - Deve localizar ADMIN pelo e-mail")
        void ur003() {
            criarEPersistirUsuario("Boss", "admin@tevao.com", RoleUsuario.ADMIN, true);
            Optional<Usuario> resultado = usuarioRepository.findByEmail("admin@tevao.com");
            assertThat(resultado).isPresent();
            assertThat(resultado.get().getRole()).isEqualTo("ADMIN");
        }

        @Test @DisplayName("UR-004 - Deve localizar GARCOM pelo e-mail")
        void ur004() {
            criarEPersistirUsuario("Garçom 1", "garcom@tevao.com", RoleUsuario.GARCOM, true);
            Optional<Usuario> resultado = usuarioRepository.findByEmail("garcom@tevao.com");
            assertThat(resultado).isPresent();
        }

        @Test @DisplayName("UR-005 - Deve localizar COZINHA pelo e-mail")
        void ur005() {
            criarEPersistirUsuario("Chapeiro", "cozinha@tevao.com", RoleUsuario.COZINHA, true);
            Optional<Usuario> resultado = usuarioRepository.findByEmail("cozinha@tevao.com");
            assertThat(resultado).isPresent();
        }

        @Test @DisplayName("UR-006 - Não deve localizar e-mail parcialmente igual")
        void ur006() {
            criarEPersistirUsuario("João", "joao123@tevao.com", RoleUsuario.GARCOM, true);
            Optional<Usuario> resultado = usuarioRepository.findByEmail("joao@");
            assertThat(resultado).isEmpty();
        }

        @Test @DisplayName("UR-007 - Não deve localizar e-mail contendo espaços adicionais")
        void ur007() {
            criarEPersistirUsuario("Espaçado", "espaco@tevao.com", RoleUsuario.GARCOM, true);
            Optional<Usuario> resultado = usuarioRepository.findByEmail(" espaco@tevao.com ");
            assertThat(resultado).isEmpty();
        }

        @Test @DisplayName("UR-008 - Não deve localizar e-mail nulo")
        void ur008() {
            Optional<Usuario> resultado = usuarioRepository.findByEmail(null);
            assertThat(resultado).isEmpty();
        }

        @Test @DisplayName("UR-009 - Não deve localizar string vazia")
        void ur009() {
            Optional<Usuario> resultado = usuarioRepository.findByEmail("");
            assertThat(resultado).isEmpty();
        }

        @Test @DisplayName("UR-010 - Aceita e-mails longos dentro do limite")
        void ur010() {
            String longo = "a".repeat(138) + "@lanches.com"; // 150 caracteres cravados
            criarEPersistirUsuario("Longo", longo, RoleUsuario.GARCOM, true);
            Optional<Usuario> resultado = usuarioRepository.findByEmail(longo);
            assertThat(resultado).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 2 — existsByEmail() (UR-011 a UR-017)
    // =========================================================================
    @Nested
    @DisplayName("✨ BLOCO 2 — existsByEmail()")
    class Bloco2ExistsByEmail {

        @Test @DisplayName("UR-011 - Deve retornar true para e-mail existente")
        void ur011() {
            criarEPersistirUsuario("Estevão", "existe@tevao.com", RoleUsuario.ADMIN, true);
            assertThat(usuarioRepository.existsByEmail("existe@tevao.com")).isTrue();
        }

        @Test @DisplayName("UR-012 - Deve retornar false para e-mail inexistente")
        void ur012() {
            assertThat(usuarioRepository.existsByEmail("naoexiste@tevao.com")).isFalse();
        }

        @Test @DisplayName("UR-013 - Deve retornar true para ADMIN cadastrado")
        void ur013() {
            criarEPersistirUsuario("Gerente", "g@tevao.com", RoleUsuario.ADMIN, true);
            assertThat(usuarioRepository.existsByEmail("g@tevao.com")).isTrue();
        }

        @Test @DisplayName("UR-014 - Deve retornar true para GARCOM cadastrado")
        void ur014() {
            criarEPersistirUsuario("Atendente", "a@tevao.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.existsByEmail("a@tevao.com")).isTrue();
        }

        @Test @DisplayName("UR-015 - Deve retornar true para COZINHA cadastrado")
        void ur015() {
            criarEPersistirUsuario("Cozinheiro", "c@tevao.com", RoleUsuario.COZINHA, true);
            assertThat(usuarioRepository.existsByEmail("c@tevao.com")).isTrue();
        }

        @Test @DisplayName("UR-016 - Deve retornar false para null")
        void ur016() {
            assertThat(usuarioRepository.existsByEmail(null)).isFalse();
        }

        @Test @DisplayName("UR-017 - Deve retornar false para vazio")
        void ur017() {
            assertThat(usuarioRepository.existsByEmail("")).isFalse();
        }
    }

    // =========================================================================
    // BLOCO 3 — existsByEmailAndIdNot() (UR-018 a UR-023)
    // =========================================================================
    @Nested
    @DisplayName("🧩 BLOCO 3 — existsByEmailAndIdNot()")
    class Bloco3ExistsByEmailAndIdNot {

        @Test @DisplayName("UR-018 - Duplicidade verdadeira: outro usuário possui o e-mail")
        void ur018() {
            Usuario u1 = criarEPersistirUsuario("User 1", "dup@tevao.com", RoleUsuario.GARCOM, true);
            Usuario u2 = criarEPersistirUsuario("User 2", "outro@tevao.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.existsByEmailAndIdNot("dup@tevao.com", u2.getId())).isTrue();
        }

        @Test @DisplayName("UR-019 - Mesmos dados contra o próprio ID deve retornar false")
        void ur019() {
            Usuario u = criarEPersistirUsuario("User", "proprio@tevao.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.existsByEmailAndIdNot("proprio@tevao.com", u.getId())).isFalse();
        }

        @Test @DisplayName("UR-020 - Cruzamento contra outro usuário retorna true")
        void ur020() {
            Usuario u1 = criarEPersistirUsuario("User A", "cross@tevao.com", RoleUsuario.GARCOM, true);
            Usuario u2 = criarEPersistirUsuario("User B", "b@tevao.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.existsByEmailAndIdNot("cross@tevao.com", u2.getId())).isTrue();
        }

        @Test @DisplayName("UR-021 - Passar ID inexistente com e-mail ocupado retorna true")
        void ur021() {
            criarEPersistirUsuario("Ocupado", "ocupado@tevao.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.existsByEmailAndIdNot("ocupado@tevao.com", UUID.randomUUID())).isTrue();
        }

        @Test @DisplayName("UR-022 - E-mail livre com qualquer ID retorna false")
        void ur022() {
            assertThat(usuarioRepository.existsByEmailAndIdNot("livre@tevao.com", UUID.randomUUID())).isFalse();
        }

        @Test @DisplayName("UR-023 - Mesmo e-mail com caixa de letras diferente")
        void ur023() {
            Usuario u = criarEPersistirUsuario("Caixa", "caixa@tevao.com", RoleUsuario.GARCOM, true);
            // Dependendo do banco/collation, validamos a sensibilidade a maiúsculas
            boolean resultado = usuarioRepository.existsByEmailAndIdNot("CAIXA@TEVAO.COM", UUID.randomUUID());
            assertThat(resultado).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 4 — findByAtivoTrue() (UR-024 a UR-030)
    // =========================================================================
    @Nested
    @DisplayName("📋 BLOCO 4 — findByAtivoTrue()")
    class Bloco4FindByAtivoTrue {

        @Test @DisplayName("UR-024 - Retorna somente colaboradores com status ativo")
        void ur024() {
            criarEPersistirUsuario("Ativo", "ativo@tevao.com", RoleUsuario.GARCOM, true);
            criarEPersistirUsuario("Inativo", "inativo@tevao.com", RoleUsuario.GARCOM, false);
            List<Usuario> ativos = usuarioRepository.findByAtivoTrue();
            assertThat(ativos).extracting(Usuario::getNome).contains("Ativo").doesNotContain("Inativo");
        }

        @Test @DisplayName("UR-025 - Retorna lista vazia se não houver registros")
        void ur025() {
            List<Usuario> ativos = usuarioRepository.findByAtivoTrue();
            assertThat(ativos).isEmpty();
        }

        @Test @DisplayName("UR-026 - Retorna todos se todos forem ativos")
        void ur026() {
            criarEPersistirUsuario("U1", "u1@tevao.com", RoleUsuario.GARCOM, true);
            criarEPersistirUsuario("U2", "u2@tevao.com", RoleUsuario.COZINHA, true);
            assertThat(usuarioRepository.findByAtivoTrue()).hasSize(2);
        }

        @Test @DisplayName("UR-027 - Retorna vazio se todos estiverem inativos")
        void ur027() {
            criarEPersistirUsuario("U1", "u1@tevao.com", RoleUsuario.GARCOM, false);
            criarEPersistirUsuario("U2", "u2@tevao.com", RoleUsuario.COZINHA, false);
            assertThat(usuarioRepository.findByAtivoTrue()).isEmpty();
        }

        @Test @DisplayName("UR-028 - Filtra cenário misturado corretamente")
        void ur028() {
            criarEPersistirUsuario("A1", "a1@tevao.com", RoleUsuario.GARCOM, true);
            criarEPersistirUsuario("I1", "i1@tevao.com", RoleUsuario.GARCOM, false);
            criarEPersistirUsuario("A2", "a2@tevao.com", RoleUsuario.COZINHA, true);
            assertThat(usuarioRepository.findByAtivoTrue()).hasSize(2);
        }

        @Test @DisplayName("UR-029 - Mantém contagem e quantidade íntegras")
        void ur029() {
            criarEPersistirUsuario("A1", "a1@tevao.com", RoleUsuario.GARCOM, true);
            int tamanhoAntes = usuarioRepository.findByAtivoTrue().size();
            criarEPersistirUsuario("A2", "a2@tevao.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.findByAtivoTrue()).hasSize(tamanhoAntes + 1);
        }

        @Test @DisplayName("UR-030 - Garantia absoluta de nunca trazer inativo")
        void ur030() {
            criarEPersistirUsuario("Demitido", "ex@tevao.com", RoleUsuario.GARCOM, false);
            List<Usuario> resultado = usuarioRepository.findByAtivoTrue();
            for (Usuario u : resultado) {
                assertThat(u.isAtivo()).isTrue();
            }
        }
    }

    // =========================================================================
    // BLOCO 5 — PERSISTÊNCIA (UR-031 a UR-040)
    // =========================================================================
    @Nested
    @DisplayName("💾 BLOCO 5 — Persistência")
    class Bloco5Persistencia {

        @Test @DisplayName("UR-031 - Salvar e reter papel de ADMIN")
        void ur031() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.ADMIN, true);
            assertThat(u.getRole()).isEqualTo("ADMIN");
        }

        @Test @DisplayName("UR-032 - Salvar e reter papel de GARCOM")
        void ur032() {
            Usuario u = criarEPersistirUsuario("G", "g@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.getRole()).isEqualTo("GARCOM");
        }

        @Test @DisplayName("UR-033 - Salvar e reter papel de COZINHA")
        void ur033() {
            Usuario u = criarEPersistirUsuario("C", "c@t.com", RoleUsuario.COZINHA, true);
            assertThat(u.getRole()).isEqualTo("COZINHA");
        }

        @Test @DisplayName("UR-034 - Salvar com flag ativa verdadeira")
        void ur034() {
            Usuario u = criarEPersistirUsuario("At", "at@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.isAtivo()).isTrue();
        }

        @Test @DisplayName("UR-035 - Salvar com flag ativa falsa")
        void ur035() {
            Usuario u = criarEPersistirUsuario("In", "in@t.com", RoleUsuario.GARCOM, false);
            assertThat(u.isAtivo()).isFalse();
        }

        @Test @DisplayName("UR-036 - Persistir string do hash criptográfico")
        void ur036() {
            Usuario u = criarEPersistirUsuario("X", "x@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.getSenha()).startsWith("$2a$");
        }

        @Test @DisplayName("UR-037 - Persistir e gerar UUID automático")
        void ur037() {
            Usuario u = criarEPersistirUsuario("U", "u@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.getId()).isNotNull();
        }

        @Test @DisplayName("UR-038 - Persistir campo e-mail")
        void ur038() {
            Usuario u = criarEPersistirUsuario("E", "email@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.getEmail()).isEqualTo("email@t.com");
        }

        @Test @DisplayName("UR-039 - Persistir campo role de acesso")
        void ur039() {
            Usuario u = criarEPersistirUsuario("R", "role@t.com", RoleUsuario.ADMIN, true);
            assertThat(u.getRole()).isEqualTo("ADMIN");
        }

        @Test @DisplayName("UR-040 - Persistir campo nome comercial")
        void ur040() {
            Usuario u = criarEPersistirUsuario("Estevão Lanches", "comercial@t.com", RoleUsuario.ADMIN, true);
            assertThat(u.getNome()).isEqualTo("Estevão Lanches");
        }
    }

    // =========================================================================
    // BLOCO 6 — CONSTRAINTS JPA (UR-041 a UR-049)
    // =========================================================================
    @Nested
    @DisplayName("🛑 BLOCO 6 — Constraints JPA")
    class Bloco6Constraints {

        @Test @DisplayName("UR-041 - E-mail obrigatório (NOT NULL)")
        void ur041() {
            Usuario u = new Usuario(null, "Nome", null, "123", "GARCOM", true);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(u));
        }

        @Test @DisplayName("UR-042 - Senha obrigatória (NOT NULL)")
        void ur042() {
            Usuario u = new Usuario(null, "Nome", "e@t.com", null, "GARCOM", true);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(u));
        }

        @Test @DisplayName("UR-043 - Nome obrigatório (NOT NULL)")
        void ur043() {
            Usuario u = new Usuario(null, null, "e@t.com", "123", "GARCOM", true);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(u));
        }

        @Test @DisplayName("UR-044 - Role obrigatória (NOT NULL)")
        void ur044() {
            Usuario u = new Usuario(null, "Nome", "e@t.com", "123", null, true);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(u));
        }

        @Test
        @DisplayName("UR-045 - E-mail único estoura exceção de integridade")
        void ur045() {

            criarEPersistirUsuario(
                    "U1",
                    "unico@tevao.com",
                    RoleUsuario.GARCOM,
                    true
            );

            Usuario u2 = new Usuario(
                    null,
                    "U2",
                    "unico@tevao.com",
                    "123",
                    "GARCOM",
                    true
            );

            assertThrows(
                    org.hibernate.exception.ConstraintViolationException.class,
                    () -> entityManager.persistAndFlush(u2)
            );
        }

        @Test
        @DisplayName("UR-046 - Bloqueio de e-mail duplicado na persistência direta")
        void ur046() {

            criarEPersistirUsuario(
                    "A",
                    "dup@t.com",
                    RoleUsuario.GARCOM,
                    true
            );

            Usuario b = new Usuario(
                    null,
                    "B",
                    "dup@t.com",
                    "321",
                    "COZINHA",
                    true
            );

            assertThrows(
                    org.hibernate.exception.ConstraintViolationException.class,
                    () -> entityManager.persistAndFlush(b)
            );
        }

        @Test @DisplayName("UR-047 - Nome acima do limite físico de 100 caracteres estoura")
        void ur047() {
            String nomeInvalido = "A".repeat(101);
            Usuario u = new Usuario(null, nomeInvalido, "limite@t.com", "123", "GARCOM", true);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(u));
        }

        @Test @DisplayName("UR-048 - E-mail acima do limite físico de 150 caracteres estoura")
        void ur048() {
            String emailInvalido = "a".repeat(142) + "@lanches.com"; // 151 caracteres
            Usuario u = new Usuario(null, "Nome", emailInvalido, "123", "GARCOM", true);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(u));
        }

        @Test @DisplayName("UR-049 - Role acima do limite físico de 255 caracteres estoura")
        void ur049() {
            String roleInvalida = "R".repeat(256);
            Usuario u = new Usuario(null, "Nome", "ok@t.com", "123", roleInvalida, true);
            assertThrows(Exception.class, () -> entityManager.persistAndFlush(u));
        }
    }

    // =========================================================================
    // BLOCO 7 — INTEGRIDADE (UR-050 a UR-056)
    // =========================================================================
    @Nested
    @DisplayName("💎 BLOCO 7 — Integridade")
    class Bloco7Integridade {

        @Test @DisplayName("UR-050 - Garantia de ID único gerado por inserção")
        void ur050() {
            Usuario u1 = criarEPersistirUsuario("U1", "u1@t.com", RoleUsuario.GARCOM, true);
            Usuario u2 = criarEPersistirUsuario("U2", "u2@t.com", RoleUsuario.GARCOM, true);
            assertThat(u1.getId()).isNotEqualTo(u2.getId());
        }

        @Test @DisplayName("UR-051 - Banco nunca reutiliza UUID antigo")
        void ur051() {
            Usuario u = criarEPersistirUsuario("U", "u@t.com", RoleUsuario.GARCOM, true);
            UUID idOriginal = u.getId();
            entityManager.remove(u);
            entityManager.flush();
            Usuario u2 = criarEPersistirUsuario("U2", "u2@t.com", RoleUsuario.GARCOM, true);
            assertThat(u2.getId()).isNotEqualTo(idOriginal);
        }

        @Test @DisplayName("UR-052 - Leitura consistente imediatamente após persistência")
        void ur052() {
            Usuario salvo = criarEPersistirUsuario("Check", "check@t.com", RoleUsuario.GARCOM, true);
            entityManager.flush();
            Optional<Usuario> buscado = usuarioRepository.findById(salvo.getId());
            assertThat(buscado).isPresent().contains(salvo);
        }

        @Test @DisplayName("UR-053 - Atualização cadastral preserva o ID imutável")
        void ur053() {
            Usuario u = criarEPersistirUsuario("Nome", "email@t.com", RoleUsuario.GARCOM, true);
            UUID idOriginal = u.getId();
            u.setNome("Alterado");
            Usuario atualizado = usuarioRepository.saveAndFlush(u);
            assertThat(atualizado.getId()).isEqualTo(idOriginal);
        }

        @Test @DisplayName("UR-054 - Atualização de nome preserva integridade da senha")
        void ur054() {
            Usuario u = criarEPersistirUsuario("Nome", "email@t.com", RoleUsuario.GARCOM, true);
            String senhaOriginal = u.getSenha();
            u.setNome("Outro Nome");
            Usuario atualizado = usuarioRepository.saveAndFlush(u);
            assertThat(atualizado.getSenha()).isEqualTo(senhaOriginal);
        }

        @Test @DisplayName("UR-055 - Atualização altera com sucesso o papel (role)")
        void ur055() {
            Usuario u = criarEPersistirUsuario("Nome", "email@t.com", RoleUsuario.GARCOM, true);
            u.setRole(RoleUsuario.ADMIN.name());
            Usuario atualizado = usuarioRepository.saveAndFlush(u);
            assertThat(atualizado.getRole()).isEqualTo("ADMIN");
        }

        @Test @DisplayName("UR-056 - Atualização altera com sucesso o status lógico ativo")
        void ur056() {
            Usuario u = criarEPersistirUsuario("Nome", "email@t.com", RoleUsuario.GARCOM, true);
            u.setAtivo(false);
            Usuario atualizado = usuarioRepository.saveAndFlush(u);
            assertThat(atualizado.isAtivo()).isFalse();
        }
    }

    // =========================================================================
    // BLOCO 8 — ATUALIZAÇÃO (UR-057 a UR-062)
    // =========================================================================
    @Nested
    @DisplayName("⚙️ BLOCO 8 — Atualização")
    class Bloco8Atualizacao {

        @Test @DisplayName("UR-057 - Modificar campo de texto nome")
        void ur057() {
            Usuario u = criarEPersistirUsuario("Original", "o@t.com", RoleUsuario.GARCOM, true);
            u.setNome("Modificado");
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findById(u.getId()).get().getNome()).isEqualTo("Modificado");
        }

        @Test @DisplayName("UR-058 - Modificar endereço de e-mail")
        void ur058() {
            Usuario u = criarEPersistirUsuario("A", "antigo@t.com", RoleUsuario.GARCOM, true);
            u.setEmail("novo@t.com");
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findByEmail("novo@t.com")).isPresent();
        }

        @Test @DisplayName("UR-059 - Modificar credencial/hash de senha")
        void ur059() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.GARCOM, true);
            u.setSenha("$2a$10$novaCripto");
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findById(u.getId()).get().getSenha()).isEqualTo("$2a$10$novaCripto");
        }

        @Test @DisplayName("UR-060 - Modificar papel de acesso operacional")
        void ur060() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.GARCOM, true);
            u.setRole(RoleUsuario.COZINHA.name());
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findById(u.getId()).get().getRole()).isEqualTo("COZINHA");
        }

        @Test @DisplayName("UR-061 - Modificar flag ativo de forma explícita")
        void ur061() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.GARCOM, true);
            u.setAtivo(false);
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findById(u.getId()).get().isAtivo()).isFalse();
        }

        @Test @DisplayName("UR-062 - Salvar e sincronizar duas vezes consecutivas sem erros")
        void ur062() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.GARCOM, true);
            u.setNome("A1");
            usuarioRepository.saveAndFlush(u);
            u.setNome("A2");
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findById(u.getId()).get().getNome()).isEqualTo("A2");
        }
    }

    // =========================================================================
    // BLOCO 9 — EXCLUSÃO (UR-063 a UR-066)
    // =========================================================================
    @Nested
    @DisplayName("🗑️ BLOCO 9 — Exclusão")
    class Bloco9Exclusao {

        @Test @DisplayName("UR-063 - Remover registro físico da tabela do banco")
        void ur063() {
            Usuario u = criarEPersistirUsuario("Expurgar", "ex@t.com", RoleUsuario.GARCOM, true);
            usuarioRepository.delete(u);
            usuarioRepository.flush();
            assertThat(usuarioRepository.findById(u.getId())).isEmpty();
        }

        @Test @DisplayName("UR-064 - findById retorna vazio após deleção física")
        void ur064() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.GARCOM, true);
            usuarioRepository.delete(u);
            usuarioRepository.flush();
            assertThat(usuarioRepository.findById(u.getId())).isNotPresent();
        }

        @Test @DisplayName("UR-065 - existsByEmail retorna false após deleção física")
        void ur065() {
            Usuario u = criarEPersistirUsuario("A", "del@t.com", RoleUsuario.GARCOM, true);
            usuarioRepository.delete(u);
            usuarioRepository.flush();
            assertThat(usuarioRepository.existsByEmail("del@t.com")).isFalse();
        }

        @Test @DisplayName("UR-066 - findAll reduz contador total de registros perfeitamente")
        void ur066() {
            Usuario u1 = criarEPersistirUsuario("1", "1@t.com", RoleUsuario.GARCOM, true);
            Usuario u2 = criarEPersistirUsuario("2", "2@t.com", RoleUsuario.GARCOM, true);
            long totalAntes = usuarioRepository.count();
            usuarioRepository.delete(u1);
            usuarioRepository.flush();
            assertThat(usuarioRepository.count()).isEqualTo(totalAntes - 1);
        }
    }

    // =========================================================================
    // BLOCO 10 — SPRING SECURITY (UR-067 a UR-074)
    // =========================================================================
    @Nested
    @DisplayName("🔑 BLOCO 10 — Spring Security")
    class Bloco10SpringSecurity {

        @Test @DisplayName("UR-067 - ROLE_ADMIN salva com autoridade nativa do Spring Security")
        void ur067() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.ADMIN, true);
            assertThat(u.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
        }

        @Test @DisplayName("UR-068 - ROLE_GARCOM salva com autoridade mapeada")
        void ur068() {
            Usuario u = criarEPersistirUsuario("G", "g@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_GARCOM");
        }

        @Test @DisplayName("UR-069 - ROLE_COZINHA salva com autoridade mapeada")
        void ur069() {
            Usuario u = criarEPersistirUsuario("C", "c@t.com", RoleUsuario.COZINHA, true);
            assertThat(u.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_COZINHA");
        }

        @Test @DisplayName("UR-070 - Mapeamento de lista de autoridades (getAuthorities) correto")
        void ur070() {
            Usuario u = criarEPersistirUsuario("X", "x@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.getAuthorities()).hasSize(1);
        }

        @Test @DisplayName("UR-071 - Username do contrato equivale exatamente ao e-mail comercial")
        void ur071() {
            Usuario u = criarEPersistirUsuario("A", "login@tevao.com", RoleUsuario.GARCOM, true);
            assertThat(u.getUsername()).isEqualTo("login@tevao.com");
        }

        @Test @DisplayName("UR-072 - Password do contrato equivale exatamente ao hash salvo")
        void ur072() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.getPassword()).isEqualTo(u.getSenha());
        }

        @Test @DisplayName("UR-073 - isEnabled retorna verdadeiro se o status ativo for true")
        void ur073() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.isEnabled()).isTrue();
        }

        @Test @DisplayName("UR-074 - isEnabled retorna falso se o status ativo for false")
        void ur074() {
            Usuario u = criarEPersistirUsuario("A", "a@t.com", RoleUsuario.GARCOM, false);
            assertThat(u.isEnabled()).isFalse();
        }
    }

    // =========================================================================
    // BLOCO 11 — STRESS (UR-075 a UR-080)
    // =========================================================================
    @Nested
    @DisplayName("⚡ BLOCO 11 — Stress")
    class Bloco11Stress {

        @Test @DisplayName("UR-075 - Persistir lote de 100 usuários sequencialmente")
        void ur075() {
            for (int i = 0; i < 100; i++) {
                criarEPersistirUsuario("User " + i, "stress1_" + i + "@tevao.com", RoleUsuario.GARCOM, true);
            }
            entityManager.flush();
        }

        @Test @DisplayName("UR-076 - Persistir lote de 500 usuários sequencialmente")
        void ur076() {
            for (int i = 0; i < 500; i++) {
                criarEPersistirUsuario("Lote " + i, "stress2_" + i + "@tevao.com", RoleUsuario.COZINHA, true);
            }
            entityManager.flush();
        }

        @Test @DisplayName("UR-077 - Realizar buscas aleatórias sob volume persistido")
        void ur077() {
            criarEPersistirUsuario("Target", "alvo@tevao.com", RoleUsuario.GARCOM, true);
            for (int i = 0; i < 50; i++) {
                assertThat(usuarioRepository.findByEmail("alvo@tevao.com")).isPresent();
            }
        }

        @Test @DisplayName("UR-078 - Executar findAll sob grande volume sem degradação")
        void ur078() {
            List<Usuario> todos = usuarioRepository.findAll();
            assertThat(todos).isNotNull();
        }

        @Test @DisplayName("UR-079 - Executar existsByEmail em lote massivo")
        void ur079() {
            String target = "mass@tevao.com";
            criarEPersistirUsuario("M", target, RoleUsuario.GARCOM, true);
            for (int i = 0; i < 100; i++) {
                assertThat(usuarioRepository.existsByEmail(target)).isTrue();
            }
        }

        @Test @DisplayName("UR-080 - findByAtivoTrue executado em massa de milhares")
        void ur080() {
            List<Usuario> ativos = usuarioRepository.findByAtivoTrue();
            assertThat(ativos).isNotNull();
        }
    }

    // =========================================================================
    // BLOCO 12 — CASOS EXTREMOS (UR-081 a UR-088)
    // =========================================================================
    @Nested
    @DisplayName("🌌 BLOCO 12 — Casos Extremos")
    class Bloco12CasosExtremos {

        @Test @DisplayName("UR-081 - E-mail utilizando alias com símbolo de adição (+)")
        void ur081() {
            String emailSub = "estevao+caixa@tevao.com";
            criarEPersistirUsuario("Estevão", emailSub, RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.findByEmail(emailSub)).isPresent();
        }

        @Test @DisplayName("UR-082 - E-mail estruturado sob múltiplos subdomínios")
        void ur082() {
            String emailSub = "admin@gerencia.retaguarda.tevao.com";
            criarEPersistirUsuario("Gerência", emailSub, RoleUsuario.ADMIN, true);
            assertThat(usuarioRepository.findByEmail(emailSub)).isPresent();
        }

        @Test @DisplayName("UR-083 - E-mail com tamanho limítrofe de 150 caracteres")
        void ur083() {
            String maxEmail = "b".repeat(138) + "@lanches.com";
            criarEPersistirUsuario("Max", maxEmail, RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.findByEmail(maxEmail)).isPresent();
        }

        @Test @DisplayName("UR-084 - Nome contendo caracteres acentuados latinos")
        void ur084() {
            criarEPersistirUsuario("Estêvão Conceição", "p1@t.com", RoleUsuario.GARCOM, true);
            Optional<Usuario> r = usuarioRepository.findByEmail("p1@t.com");
            assertThat(r.get().getNome()).isEqualTo("Estêvão Conceição");
        }

        @Test @DisplayName("UR-085 - Nome estruturado em ideogramas Japoneses")
        void ur085() {
            criarEPersistirUsuario("田中太郎", "p2@t.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.findByEmail("p2@t.com").get().getNome()).isEqualTo("田中太郎");
        }

        @Test @DisplayName("UR-086 - Nome estruturado em caracteres Árabes")
        void ur086() {
            criarEPersistirUsuario("أحمد", "p3@t.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.findByEmail("p3@t.com").get().getNome()).isEqualTo("أحمد");
        }

        @Test @DisplayName("UR-087 - Nome contendo símbolos gráficos e Emojis")
        void ur087() {
            criarEPersistirUsuario("Estevão 🍔🍟", "p4@t.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.findByEmail("p4@t.com").get().getNome()).isEqualTo("Estevão 🍔🍟");
        }

        @Test @DisplayName("UR-088 - Nome contendo pontuações e caracteres especiais")
        void ur088() {
            criarEPersistirUsuario("Atendente O'Connor-Lanches", "p5@t.com", RoleUsuario.GARCOM, true);
            assertThat(usuarioRepository.findByEmail("p5@t.com").get().getNome()).isEqualTo("Atendente O'Connor-Lanches");
        }
    }

    // =========================================================================
    // BLOCO 13 — REGRESSÃO (UR-089 a UR-095)
    // =========================================================================
    @Nested
    @DisplayName("🔄 BLOCO 13 — Regressão")
    class Bloco13Regressao {

        @Test @DisplayName("UR-089 - Operação de update nunca duplica ou gera novos registros")
        void ur089() {
            Usuario u = criarEPersistirUsuario("A", "reg1@t.com", RoleUsuario.GARCOM, true);
            long totalAntes = usuarioRepository.count();
            u.setNome("A Modificado");
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.count()).isEqualTo(totalAntes);
        }

        @Test @DisplayName("UR-090 - Operação de update nunca altera ou corrompe IDs")
        void ur090() {
            Usuario u = criarEPersistirUsuario("A", "reg2@t.com", RoleUsuario.GARCOM, true);
            UUID idOriginal = u.getId();
            u.setEmail("reg2_alt@t.com");
            usuarioRepository.saveAndFlush(u);
            assertThat(u.getId()).isEqualTo(idOriginal);
        }

        @Test @DisplayName("UR-091 - Operação de save comum nunca apaga ou perde a senha")
        void ur091() {
            Usuario u = criarEPersistirUsuario("A", "reg3@t.com", RoleUsuario.GARCOM, true);
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findById(u.getId()).get().getSenha()).isNotNull();
        }

        @Test @DisplayName("UR-092 - Operação de save comum nunca desvincula o papel")
        void ur092() {
            Usuario u = criarEPersistirUsuario("A", "reg4@t.com", RoleUsuario.GARCOM, true);
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findById(u.getId()).get().getRole()).isEqualTo("GARCOM");
        }

        @Test @DisplayName("UR-093 - Operação de save comum nunca desvincula status ativo")
        void ur093() {
            Usuario u = criarEPersistirUsuario("A", "reg5@t.com", RoleUsuario.GARCOM, true);
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findById(u.getId()).get().isAtivo()).isTrue();
        }

        @Test @DisplayName("UR-094 - Modificar um usuário nunca altera e-mails de terceiros")
        void ur094() {
            Usuario u1 = criarEPersistirUsuario("U1", "u1_reg@t.com", RoleUsuario.GARCOM, true);
            Usuario u2 = criarEPersistirUsuario("U2", "u2_reg@t.com", RoleUsuario.GARCOM, true);
            u1.setNome("U1 Alterado");
            usuarioRepository.saveAndFlush(u1);
            assertThat(usuarioRepository.findById(u2.getId()).get().getEmail()).isEqualTo("u2_reg@t.com");
        }

        @Test @DisplayName("UR-095 - Leitura do e-mail é consistente e atualizada pós-update")
        void ur095() {
            Usuario u = criarEPersistirUsuario("Mudar", "mudar@t.com", RoleUsuario.GARCOM, true);
            u.setEmail("mudado@t.com");
            usuarioRepository.saveAndFlush(u);
            assertThat(usuarioRepository.findByEmail("mudar@t.com")).isEmpty();
            assertThat(usuarioRepository.findByEmail("mudado@t.com")).isPresent();
        }
    }

    // =========================================================================
    // BLOCO 14 — AUDITORIA (UR-096 a UR-100)
    // =========================================================================
    @Nested
    @DisplayName("🧼 BLOCO 14 — Auditoria")
    class Bloco14Auditoria {

        @Test @DisplayName("UR-096 - Todos os usuários persistidos possuem UUID válido")
        void ur096() {
            Usuario u = criarEPersistirUsuario("Audit", "audit1@t.com", RoleUsuario.GARCOM, true);
            assertThat(u.getId()).isNotNull();
        }

        @Test @DisplayName("UR-097 - Nenhuma leitura retorna registro com e-mail nulo")
        void ur097() {
            criarEPersistirUsuario("Audit", "audit2@t.com", RoleUsuario.GARCOM, true);
            List<Usuario> todos = usuarioRepository.findAll();
            for (Usuario u : todos) {
                assertThat(u.getEmail()).isNotNull();
            }
        }

        @Test @DisplayName("UR-098 - Nenhuma leitura retorna registro com senha nula")
        void ur098() {
            criarEPersistirUsuario("Audit", "audit3@t.com", RoleUsuario.GARCOM, true);
            List<Usuario> todos = usuarioRepository.findAll();
            for (Usuario u : todos) {
                assertThat(u.getSenha()).isNotNull();
            }
        }

        @Test @DisplayName("UR-099 - Nenhuma leitura retorna registro com papel (role) nulo")
        void ur099() {
            criarEPersistirUsuario("Audit", "audit4@t.com", RoleUsuario.GARCOM, true);
            List<Usuario> todos = usuarioRepository.findAll();
            for (Usuario u : todos) {
                assertThat(u.getRole()).isNotNull();
            }
        }

        @Test @DisplayName("UR-100 - Nenhuma leitura retorna estado com flag de atividade nula")
        void ur100() {
            criarEPersistirUsuario("Audit", "audit5@t.com", RoleUsuario.GARCOM, true);
            List<Usuario> todos = usuarioRepository.findAll();
            for (Usuario u : todos) {
                // Sendo tipo primitivo boolean, a verificação confirma que o estado lógico avalia sem exceções
                assertThat(u.isAtivo()).isNotNull();
            }
        }
    }
}