package com.paullomaggio.estevaoLanches.repositories;

import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.enums.RoleUsuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    // Métodos auxiliares para acelerar a criação de cenários de teste
    private Usuario criarEPersistirUsuario(String nome, String email, RoleUsuario role, Boolean ativo) {
        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha("$2a$10$hashQualquer");
        usuario.setRole(role);
        usuario.setAtivo(ativo);
        return entityManager.persist(usuario);
    }

    // ==========================================
    // 1. TESTES DO MÉTODO findByEmail()
    // ==========================================

    @Test
    @DisplayName("Deve localizar com sucesso um usuário pelo e-mail exato")
    void findByEmailCenario1() {
        String emailAlvo = "atendente@tevao.com";
        criarEPersistirUsuario("Carlos Caixa", emailAlvo, RoleUsuario.GARCOM, true);

        Optional<Usuario> resultado = usuarioRepository.findByEmail(emailAlvo);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getEmail()).isEqualTo(emailAlvo);
        assertThat(resultado.get().getNome()).isEqualTo("Carlos Caixa");
    }

    @Test
    @DisplayName("Deve retornar um Optional vazio caso o e-mail não exista no banco")
    void findByEmailCenario2() {
        Optional<Usuario> resultado = usuarioRepository.findByEmail("fantasma@tevao.com");

        assertThat(resultado).isEmpty();
    }

    // ==========================================
    // 2. TESTES DO MÉTODO existsByEmail()
    // ==========================================

    @Test
    @DisplayName("Deve retornar verdadeiro (true) se o e-mail já estiver ocupado")
    void existsByEmailCenario1() {
        String emailOcupado = "gerente@tevao.com";
        criarEPersistirUsuario("Dono Estêvão", emailOcupado, RoleUsuario.ADMIN, true);

        boolean existe = usuarioRepository.existsByEmail(emailOcupado);

        assertThat(existe).isTrue();
    }

    @Test
    @DisplayName("Deve retornar falso (false) se o e-mail estiver livre para cadastro")
    void existsByEmailCenario2() {
        boolean existe = usuarioRepository.existsByEmail("novo.email@tevao.com");

        assertThat(existe).isFalse();
    }

    // ==========================================
    // 3. TESTES DO MÉTODO findByAtivoTrue()
    // ==========================================

    @Test
    @DisplayName("Deve filtrar o banco e trazer somente os funcionários com status ativo igual a true")
    void findByAtivoTrueCenario1() {
        criarEPersistirUsuario("Chapeiro Ativo", "chapeiro1@tevao.com", RoleUsuario.COZINHA, true);
        criarEPersistirUsuario("Caixa Ativo", "caixa1@tevao.com", RoleUsuario.GARCOM, true);
        criarEPersistirUsuario("Funcionário Antigo", "demitido@tevao.com", RoleUsuario.COZINHA, false); // Inativo

        List<Usuario> ativos = usuarioRepository.findByAtivoTrue();

        assertThat(ativos).hasSize(2);
        assertThat(ativos).extracting(Usuario::getNome)
                .containsExactlyInAnyOrder("Chapeiro Ativo", "Caixa Ativo")
                .doesNotContain("Funcionário Antigo");
    }

    @Test
    @DisplayName("Deve retornar uma lista vazia se todos os usuários cadastrados estiverem inativos")
    void findByAtivoTrueCenario2() {
        criarEPersistirUsuario("Ex-Funcionário 1", "antigo1@tevao.com", RoleUsuario.GARCOM, false);
        criarEPersistirUsuario("Ex-Funcionário 2", "antigo2@tevao.com", RoleUsuario.COZINHA, false);

        List<Usuario> ativos = usuarioRepository.findByAtivoTrue();

        assertThat(ativos).isEmpty();
    }
}