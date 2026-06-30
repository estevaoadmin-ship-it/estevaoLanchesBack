package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.UsuarioRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.UsuarioResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.enums.RoleUsuario;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioService - colaboradores, credenciais e RBAC")
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private UsuarioService usuarioService;

    private Usuario usuarioAtivo;
    private Usuario usuarioInativo;
    private UUID idAtivo;
    private UUID idInativo;

    @BeforeEach
    void setUp() {
        idAtivo = UUID.randomUUID();
        idInativo = UUID.randomUUID();

        usuarioAtivo = new Usuario(idAtivo, "Joao Caixa", "joao@tevao.com", "senhaCripto123", "GARCOM", true);
        usuarioInativo = new Usuario(idInativo, "Maria Cozinha", "maria@tevao.com", "senhaCripto456", "COZINHA", false);
    }

    @Nested
    @DisplayName("1. Contrato Spring Security")
    class SpringSecurityTests {

        @Test
        @DisplayName("CT-USR-001 e CT-USR-003 - Carrega colaborador existente pelo e-mail")
        void loadUserByUsernameCenario1() {
            when(usuarioRepository.findByEmail("joao@tevao.com")).thenReturn(Optional.of(usuarioAtivo));

            UserDetails resultado = usuarioService.loadUserByUsername("joao@tevao.com");

            assertNotNull(resultado);
            assertEquals("joao@tevao.com", resultado.getUsername());
            assertEquals("senhaCripto123", resultado.getPassword());
            verify(usuarioRepository).findByEmail("joao@tevao.com");
        }

        @Test
        @DisplayName("CT-USR-002 - E-mail inexistente dispara UsernameNotFoundException")
        void loadUserByUsernameCenario2() {
            when(usuarioRepository.findByEmail("fantasma@tevao.com")).thenReturn(Optional.empty());

            assertThrows(UsernameNotFoundException.class, () -> usuarioService.loadUserByUsername("fantasma@tevao.com"));
        }

        @Test
        @DisplayName("CT-USR-004 ao CT-USR-010 - Role persistida sem prefixo e authority exposta com ROLE_")
        void ctUsr004_validarAtributosDeContratoSecurity() {
            Collection<? extends GrantedAuthority> authorities = usuarioAtivo.getAuthorities();

            assertEquals("GARCOM", usuarioAtivo.getRole());
            assertEquals("ROLE_GARCOM", authorities.iterator().next().getAuthority());
            assertTrue(usuarioAtivo.isAccountNonExpired());
            assertTrue(usuarioAtivo.isAccountNonLocked());
            assertTrue(usuarioAtivo.isCredentialsNonExpired());
            assertTrue(usuarioAtivo.isEnabled());
            assertFalse(usuarioInativo.isEnabled());
        }
    }

    @Nested
    @DisplayName("2. Listagem geral")
    class ListagemGeralTests {

        @Test
        @DisplayName("CT-USR-011, CT-USR-013 e CT-USR-014 - Lista todos os colaboradores convertidos em DTO")
        void listarTodosCenario1() {
            when(usuarioRepository.findAll()).thenReturn(List.of(usuarioAtivo, usuarioInativo));

            List<UsuarioResponseDTO> resultado = usuarioService.listarTodos();

            assertEquals(2, resultado.size());
            assertEquals("Joao Caixa", resultado.get(0).nome());
            assertEquals("GARCOM", resultado.get(0).role());
            assertEquals("Maria Cozinha", resultado.get(1).nome());
            assertFalse(resultado.get(1).ativo());
        }

        @Test
        @DisplayName("CT-USR-012 - Lista vazia quando nao ha colaboradores cadastrados")
        void listarTodosCenario2() {
            when(usuarioRepository.findAll()).thenReturn(Collections.emptyList());

            List<UsuarioResponseDTO> resultado = usuarioService.listarTodos();

            assertTrue(resultado.isEmpty());
        }
    }

    @Nested
    @DisplayName("3. Colaboradores ativos")
    class UsuariosAtivosTests {

        @Test
        @DisplayName("CT-USR-015 ao CT-USR-018 - Lista somente colaboradores ativos")
        void listarApenasAtivosCenario1() {
            when(usuarioRepository.findByAtivoTrue()).thenReturn(List.of(usuarioAtivo));

            List<UsuarioResponseDTO> resultado = usuarioService.listarApenasAtivos();

            assertEquals(1, resultado.size());
            assertEquals(idAtivo, resultado.get(0).id());
            assertTrue(resultado.get(0).ativo());
            assertEquals("GARCOM", resultado.get(0).role());
        }

        @Test
        @DisplayName("CT-USR-017 - Retorna lista vazia se nenhum colaborador estiver ativo")
        void listarApenasAtivosCenario2() {
            when(usuarioRepository.findByAtivoTrue()).thenReturn(Collections.emptyList());

            List<UsuarioResponseDTO> resultado = usuarioService.listarApenasAtivos();

            assertTrue(resultado.isEmpty());
        }
    }

    @Nested
    @DisplayName("4. Busca por ID")
    class BuscaPorIdTests {

        @Test
        @DisplayName("CT-USR-019 e CT-USR-021 - Busca colaborador existente por ID")
        void buscarPorIdCenario1() {
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));

            UsuarioResponseDTO resultado = usuarioService.buscarPorId(idAtivo);

            assertEquals(idAtivo, resultado.id());
            assertEquals("joao@tevao.com", resultado.email());
            assertEquals("GARCOM", resultado.role());
        }

        @Test
        @DisplayName("CT-USR-020 - ID inexistente dispara ResourceNotFoundException")
        void buscarPorIdCenario2() {
            when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> usuarioService.buscarPorId(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("5, 10 e 12. Cadastro e criptografia")
    class CadastroECriptografiaTests {

        @Test
        @DisplayName("CT-USR-022 ao CT-USR-024, CT-USR-028 ao CT-USR-030 - Cadastra colaborador ativo com role sem prefixo")
        void salvarCenario1() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "paulo@tevao.com", "senha123", RoleUsuario.ADMIN);
            when(usuarioRepository.existsByEmail("paulo@tevao.com")).thenReturn(false);
            when(passwordEncoder.encode("senha123")).thenReturn("hashSenha123");
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UsuarioResponseDTO resultado = usuarioService.salvar(request);

            assertEquals("Paulo", resultado.nome());
            assertEquals("paulo@tevao.com", resultado.email());
            assertEquals("ADMIN", resultado.role());
            assertTrue(resultado.ativo());

            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            assertEquals("ADMIN", captor.getValue().getRole());
            assertFalse(captor.getValue().getRole().startsWith("ROLE_"));
            assertEquals("hashSenha123", captor.getValue().getSenha());
        }

        @Test
        @DisplayName("CT-USR-025 e CT-USR-026, CT-USR-058, CT-USR-060 - Usa PasswordEncoder para mascarar senha")
        void salvarCenario3() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "paulo@tevao.com", "textoPuro123", RoleUsuario.ADMIN);
            when(usuarioRepository.existsByEmail("paulo@tevao.com")).thenReturn(false);
            when(passwordEncoder.encode("textoPuro123")).thenReturn("hashBCrypt8821");
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            usuarioService.salvar(request);

            verify(passwordEncoder).encode("textoPuro123");
        }

        @Test
        @DisplayName("CT-USR-027 e CT-USR-059 - Mantem compatibilidade quando PasswordEncoder nao estiver disponivel")
        void salvarCenario4() {
            ReflectionTestUtils.setField(usuarioService, "passwordEncoder", null);

            UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "paulo@tevao.com", "textoPuro123", RoleUsuario.ADMIN);
            when(usuarioRepository.existsByEmail("paulo@tevao.com")).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
                Usuario usuario = invocation.getArgument(0);
                assertEquals("textoPuro123", usuario.getSenha());
                return usuario;
            });

            usuarioService.salvar(request);

            ReflectionTestUtils.setField(usuarioService, "passwordEncoder", passwordEncoder);
        }
    }

    @Nested
    @DisplayName("6. Duplicidade de e-mail")
    class DuplicidadeEmailTests {

        @Test
        @DisplayName("CT-USR-031 e CT-USR-032 - Bloqueia cadastro com e-mail ja existente")
        void salvarCenario2() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "joao@tevao.com", "senha123", RoleUsuario.ADMIN);
            when(usuarioRepository.existsByEmail("joao@tevao.com")).thenReturn(true);

            assertThrows(BusinessRuleException.class, () -> usuarioService.salvar(request));
            verify(usuarioRepository, never()).save(any(Usuario.class));
        }

        @Test
        @DisplayName("CT-USR-033 ao CT-USR-035 - Mesmo e-mail com case diferente nao consulta duplicidade externa")
        void deveIgnorarCaseAoValidarEmailDuplicadoNaAtualizacao() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Joao", "JOAO@TEVAO.COM", "", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UsuarioResponseDTO resultado = usuarioService.atualizar(idAtivo, request);

            assertEquals("JOAO@TEVAO.COM", resultado.email());
            verify(usuarioRepository, never()).existsByEmailAndIdNot(anyString(), any(UUID.class));
        }
    }

    @Nested
    @DisplayName("7, 13 e 14. Atualizacao de campos")
    class AtualizacaoCamposTests {

        @Test
        @DisplayName("CT-USR-036 ao CT-USR-038, CT-USR-046 e CT-USR-047 - Atualiza dados preservando ID")
        void atualizarCenario1() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Joao Alterado", "joao@tevao.com", "", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UsuarioResponseDTO resultado = usuarioService.atualizar(idAtivo, request);

            assertEquals(idAtivo, resultado.id());
            assertEquals("Joao Alterado", resultado.nome());
            assertEquals("GARCOM", resultado.role());
            assertEquals("senhaCripto123", usuarioAtivo.getSenha());
        }

        @Test
        @DisplayName("CT-USR-039 e CT-USR-044 - Codifica senha quando alterada explicitamente")
        void atualizarCenario5() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Joao", "joao@tevao.com", "novaSenhaMaster", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(passwordEncoder.encode("novaSenhaMaster")).thenReturn("novaSenhaCripto999");
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            usuarioService.atualizar(idAtivo, request);

            verify(passwordEncoder).encode("novaSenhaMaster");
            assertEquals("novaSenhaCripto999", usuarioAtivo.getSenha());
        }

        @Test
        @DisplayName("CT-USR-040 ao CT-USR-043, CT-USR-045, CT-USR-061 ao CT-USR-063, CT-USR-077 - Preserva hash se senha vier nula ou em branco")
        void atualizarCenariosSenhaNulaEVazia() {
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UsuarioRequestDTO reqNull = new UsuarioRequestDTO("Joao", "joao@tevao.com", null, RoleUsuario.GARCOM);
            usuarioService.atualizar(idAtivo, reqNull);
            assertEquals("senhaCripto123", usuarioAtivo.getSenha());

            UsuarioRequestDTO reqVazia = new UsuarioRequestDTO("Joao", "joao@tevao.com", "   ", RoleUsuario.GARCOM);
            usuarioService.atualizar(idAtivo, reqVazia);
            assertEquals("senhaCripto123", usuarioAtivo.getSenha());

            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    @Nested
    @DisplayName("8. Atualizacao de e-mail")
    class AtualizacaoEmailTests {

        @Test
        @DisplayName("CT-USR-050 - Bloqueia e-mail pertencente a outro colaborador")
        void atualizarCenario3() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Joao", "maria@tevao.com", "", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.existsByEmailAndIdNot("maria@tevao.com", idAtivo)).thenReturn(true);

            assertThrows(BusinessRuleException.class, () -> usuarioService.atualizar(idAtivo, request));
            verify(usuarioRepository, never()).save(any(Usuario.class));
        }

        @Test
        @DisplayName("CT-USR-048 - Mantem mesmo e-mail sem consultar duplicidade")
        void atualizarCenario4() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Joao Caixa Modificado", "joao@tevao.com", "", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            usuarioService.atualizar(idAtivo, request);

            verify(usuarioRepository, never()).existsByEmailAndIdNot(anyString(), any(UUID.class));
        }
    }

    @Nested
    @DisplayName("9. Soft delete")
    class SoftDeleteTests {

        @Test
        @DisplayName("CT-USR-052 ao CT-USR-056, CT-USR-079, CT-USR-103 - Inativa colaborador sem apagar registro")
        void deletarOuInativarCenario1e3() {
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            usuarioService.deletarOuInativar(idAtivo);

            assertFalse(usuarioAtivo.isAtivo());
            assertFalse(usuarioAtivo.isEnabled());
            verify(usuarioRepository).save(usuarioAtivo);
            verify(usuarioRepository, never()).delete(any(Usuario.class));
        }

        @Test
        @DisplayName("CT-USR-057 - Inativar ID inexistente dispara ResourceNotFoundException")
        void deveLancarExcecaoAoInativarUsuarioInexistente() {
            when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> usuarioService.deletarOuInativar(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("11. Persistencia anti-nulo")
    class PersistenciaAntiNuloTests {

        @Test
        @DisplayName("CT-USR-065 - Retorno nulo no insert dispara BusinessRuleException")
        void salvarDeveLancarExcecaoQuandoPersistenciaRetornarNulo() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Falha", "falha@tevao.com", "123", RoleUsuario.GARCOM);
            when(usuarioRepository.existsByEmail("falha@tevao.com")).thenReturn(false);
            when(passwordEncoder.encode("123")).thenReturn("hash123");
            when(usuarioRepository.save(any(Usuario.class))).thenReturn(null);

            assertThrows(BusinessRuleException.class, () -> usuarioService.salvar(request));
        }

        @Test
        @DisplayName("CT-USR-066 - Retorno nulo no update dispara BusinessRuleException")
        void atualizarDeveLancarExcecaoQuandoPersistenciaRetornarNulo() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Falha", "joao@tevao.com", "", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenReturn(null);

            assertThrows(BusinessRuleException.class, () -> usuarioService.atualizar(idAtivo, request));
        }
    }

    @Nested
    @DisplayName("15 a 18. Fluxos integrados e isolamento")
    class FluxosIntegradosLanchesTests {

        @Test
        @DisplayName("CT-USR-076 e CT-USR-100 - UsuarioService cuida apenas de colaboradores e DTO nao expoe senha")
        void deveGarantirQueUsuarioServiceNaoInvoqueEscoposDeClientesExteriores() {
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UsuarioRequestDTO request = new UsuarioRequestDTO("Joao Monitorado", "joao@tevao.com", "", RoleUsuario.GARCOM);
            UsuarioResponseDTO response = usuarioService.atualizar(idAtivo, request);

            assertNotNull(response);
            assertEquals("Joao Monitorado", response.nome());
            assertEquals("GARCOM", response.role());
            assertEquals(5, UsuarioResponseDTO.class.getRecordComponents().length);
            verifyNoInteractions(passwordEncoder);
        }
    }
}
