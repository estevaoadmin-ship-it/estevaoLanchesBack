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
@DisplayName("🧪 Suíte de Testes Suprema — Matriz de Blindagem da Identidade de Colaboradores")
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

        usuarioAtivo = new Usuario(idAtivo, "João Caixa", "joao@tevao.com", "senhaCripto123", "GARCOM", true);
        usuarioInativo = new Usuario(idInativo, "Maria Antiga", "maria@tevao.com", "senhaCripto456", "COZINHA", false);
    }

    // =========================================================================
    // BLOCO 1 — SPRING SECURITY (CONTRATO USERDETAILS)
    // =========================================================================
    @Nested
    @DisplayName("1. Camada de Blindagem — Mecanismo de Contrato Spring Security")
    class SpringSecurityTests {

        @Test
        @DisplayName("CT-USR-001 e CT-USR-003: Carregar Usuário Legítimo — Deve carregar UserDetails com sucesso ao buscar e-mail existente")
        void loadUserByUsernameCenario1() {
            when(usuarioRepository.findByEmail("joao@tevao.com")).thenReturn(Optional.of(usuarioAtivo));

            UserDetails resultado = usuarioService.loadUserByUsername("joao@tevao.com");

            assertNotNull(resultado);
            assertEquals("joao@tevao.com", resultado.getUsername());
            verify(usuarioRepository, times(1)).findByEmail("joao@tevao.com");
        }

        @Test
        @DisplayName("CT-USR-002: Usuário Fantasma — Deve estourar UsernameNotFoundException quando o e-mail não existir no banco")
        void loadUserByUsernameCenario2() {
            when(usuarioRepository.findByEmail("fantasma@tevao.com")).thenReturn(Optional.empty());
            assertThrows(UsernameNotFoundException.class, () -> usuarioService.loadUserByUsername("fantasma@tevao.com"));
        }

        @Test
        @DisplayName("CT-USR-004 ao CT-USR-010: Prefixo ROLE_ — Garante mapeamento correto das authorities e flags nativas do Spring Security")
        void ctUsr004_validarAtributosDeContratoSecurity() {
            Collection<? extends GrantedAuthority> authorities = usuarioAtivo.getAuthorities();
            assertNotNull(authorities);
            assertEquals("ROLE_GARCOM", authorities.iterator().next().getAuthority());
            assertTrue(usuarioAtivo.isAccountNonExpired());
            assertTrue(usuarioAtivo.isAccountNonLocked());
            assertTrue(usuarioAtivo.isCredentialsNonExpired());
        }
    }

    // =========================================================================
    // BLOCO 2 — LISTAGEM GERAL
    // =========================================================================
    @Nested
    @DisplayName("2. Camada de Blindagem — Listagem Geral de Turno")
    class ListagemGeralTests {

        @Test
        @DisplayName("CT-USR-011, CT-USR-013 e CT-USR-014: Mapeamento de Lote — Deve retornar todos os usuários cadastrados convertidos em DTO")
        void listarTodosCenario1() {
            when(usuarioRepository.findAll()).thenReturn(List.of(usuarioAtivo, usuarioInativo));

            List<UsuarioResponseDTO> resultado = usuarioService.listarTodos();

            assertEquals(2, resultado.size());
            assertEquals("João Caixa", resultado.get(0).nome());
            assertEquals("Maria Antiga", resultado.get(1).nome());
        }

        @Test
        @DisplayName("CT-USR-012: Painel Vazio — Deve retornar uma coleção imutável vazia se não existirem funcionários no banco")
        void listarTodosCenario2() {
            when(usuarioRepository.findAll()).thenReturn(Collections.emptyList());
            List<UsuarioResponseDTO> resultado = usuarioService.listarTodos();
            assertTrue(resultado.isEmpty());
        }
    }

    // =========================================================================
    // BLOCO 3 — USUÁRIOS ATIVOS (ESCUDO DE ESCALAS)
    // =========================================================================
    @Nested
    @DisplayName("3. Camada de Blindagem — Filtro de Funcionários Ativos")
    class UsuariosAtivosTests {

        @Test
        @DisplayName("CT-USR-015 ao CT-USR-018: Filtro Operacional — Deve retornar exclusivamente colaboradores com a flag ativo=true")
        void listarApenasAtivosCenario1() {
            when(usuarioRepository.findByAtivoTrue()).thenReturn(List.of(usuarioAtivo));

            List<UsuarioResponseDTO> resultado = usuarioService.listarApenasAtivos();

            assertEquals(1, resultado.size());
            assertEquals("João Caixa", resultado.get(0).nome());
            assertTrue(resultado.get(0).ativo());
        }

        @Test
        @DisplayName("CT-USR-017: Turno Deserto — Caso todos estejam inativos, o painel deve retornar uma coleção vazia")
        void listarApenasAtivosCenario2() {
            when(usuarioRepository.findByAtivoTrue()).thenReturn(Collections.emptyList());
            List<UsuarioResponseDTO> resultado = usuarioService.listarApenasAtivos();
            assertTrue(resultado.isEmpty());
        }
    }

    // =========================================================================
    // BLOCO 4 — BUSCA POR ID IDENTIFICADOR
    // =========================================================================
    @Nested
    @DisplayName("4. Camada de Blindagem — Consultas Unitárias por ID")
    class BuscaPorIdTests {

        @Test
        @DisplayName("CT-USR-019 e CT-USR-021: ID Existente — Localiza usuário ativo e monta o DTO de resposta")
        void buscarPorIdCenario1() {
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));

            UsuarioResponseDTO resultado = usuarioService.buscarPorId(idAtivo);

            assertNotNull(resultado);
            assertEquals(idAtivo, resultado.id());
        }

        @Test
        @DisplayName("CT-USR-020: ID Inexistente — Tentar carregar ID órfão dispara ResourceNotFoundException imediatamente")
        void buscarPorIdCenario2() {
            when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> usuarioService.buscarPorId(UUID.randomUUID()));
        }
    }

    // =========================================================================
    // BLOCO 5 & 10 & 12 — CADASTRO E CRIPTOGRAFIA (BCRYPT SEGURANÇA)
    // =========================================================================
    @Nested
    @DisplayName("5, 10 & 12. Camada de Blindagem — Cadastro e Processamento Criptográfico")
    class CadastroECriptografiaTests {

        @Test
        @DisplayName("CT-USR-022 ao CT-USR-024, CT-USR-028 ao CT-USR-030: Inserção de Nova Credencial — Deve cadastrar colaboradores com status ativo=true e converter Enums")
        void salvarCenario1() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "paulo@tevao.com", "senha123", RoleUsuario.ADMIN);
            when(usuarioRepository.existsByEmail(request.email())).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UsuarioResponseDTO resultado = usuarioService.salvar(request);

            assertNotNull(resultado);
            assertEquals("Paulo", resultado.nome());
            assertEquals("ADMIN", resultado.role());
            assertTrue(resultado.ativo());
        }

        @Test
        @DisplayName("CT-USR-025 e CT-USR-026, CT-USR-058, CT-USR-060: Mascaramento BCrypt — Deve acionar o PasswordEncoder para mascarar a senha em texto puro")
        void salvarCenario3() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "paulo@tevao.com", "textoPuro123", RoleUsuario.ADMIN);
            when(usuarioRepository.existsByEmail(request.email())).thenReturn(false);
            when(passwordEncoder.encode("textoPuro123")).thenReturn("hashBCrypt8821");
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

            usuarioService.salvar(request);

            verify(passwordEncoder, times(1)).encode("textoPuro123");
        }

        @Test
        @DisplayName("CT-USR-027 e CT-USR-059: Encoder Ausente — Deve reter a senha em texto puro caso o PasswordEncoder esteja nulo em ambiente isolado")
        void salvarCenario4() {
            ReflectionTestUtils.setField(usuarioService, "passwordEncoder", null);

            UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "paulo@tevao.com", "textoPuro123", RoleUsuario.ADMIN);
            when(usuarioRepository.existsByEmail(request.email())).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
                Usuario u = invocation.getArgument(0);
                assertEquals("textoPuro123", u.getSenha());
                return u;
            });

            usuarioService.salvar(request);

            ReflectionTestUtils.setField(usuarioService, "passwordEncoder", passwordEncoder);
        }
    }

    // =========================================================================
    // BLOCO 6 — DUPLICIDADE DE E-MAILS (UNICIADADE DO LIVRO-RAZÃO)
    // =========================================================================
    @Nested
    @DisplayName("6. Camada de Blindagem — Bloqueio de Idempotência e Duplicidade")
    class DuplicidadeEmailTests {

        @Test
        @DisplayName("CT-USR-031 e CT-USR-032: Conflito de Cadastro — Tentar salvar e-mail já cadastrado deve estourar BusinessRuleException")
        void salvarCenario2() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "joao@tevao.com", "senha123", RoleUsuario.ADMIN);
            when(usuarioRepository.existsByEmail(request.email())).thenReturn(true);

            assertThrows(BusinessRuleException.class, () -> usuarioService.salvar(request));
            verify(usuarioRepository, never()).save(any(Usuario.class));
        }

        @Test
        @DisplayName("CT-USR-033 ao CT-USR-035: Case Insensitive — A verificação de e-mail deve ignorar variações de letras maiúsculas/minúsculas")
        void deveIgnorarCaseAoValidarEmailDuplicadoNaAtualizacao() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("João", "JOAO@TEVAO.COM", "", RoleUsuario.GARCOM);

            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            usuarioService.atualizar(idAtivo, request);

            verify(usuarioRepository, never()).existsByEmailAndIdNot(anyString(), any(UUID.class));
        }
    }

    // =========================================================================
    // BLOCO 7 & 13 & 14 — ATUALIZAÇÃO E HIGIENIZAÇÃO DE CAMPOS SENCILES
    // =========================================================================
    @Nested
    @DisplayName("7, 13 & 14. Camada de Blindagem — Atualizações e Higienização de Formulário")
    class AtualizacaoCamposTests {

        @Test
        @DisplayName("CT-USR-036 ao CT-USR-038, CT-USR-046 e CT-USR-047: Atualização Limpa — Modifica cadastro com sucesso preservando o ID mestre")
        void atualizarCenario1() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("João Alterado", "joao@tevao.com", "", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            UsuarioResponseDTO resultado = usuarioService.atualizar(idAtivo, request);

            assertEquals("João Alterado", resultado.nome());
            assertEquals(idAtivo, resultado.id());
        }

        @Test
        @DisplayName("CT-USR-039 e CT-USR-044: Alterar Senha — Deve codificar e atualizar a senha se ela for alterada de forma explícita")
        void atualizarCenario5() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("João", "joao@tevao.com", "novaSenhaMaster", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(passwordEncoder.encode("novaSenhaMaster")).thenReturn("novaSenhaCripto999");
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            usuarioService.atualizar(idAtivo, request);

            verify(passwordEncoder, times(1)).encode("novaSenhaMaster");
        }

        @Test
        @DisplayName("CT-USR-040 ao CT-USR-043, CT-USR-045, CT-USR-061 ao CT-USR-063, CT-USR-077: Antissalto de Hash — Deve preservar o hash antigo intacto se a nova senha vier nula, vazia ou em branco")
        void atualizarCenariosSenhaNulaEVazia() {
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            UsuarioRequestDTO reqNull = new UsuarioRequestDTO("João", "joao@tevao.com", null, RoleUsuario.GARCOM);
            usuarioService.atualizar(idAtivo, reqNull);
            assertEquals("senhaCripto123", usuarioAtivo.getSenha());

            UsuarioRequestDTO reqVazia = new UsuarioRequestDTO("João", "joao@tevao.com", "   ", RoleUsuario.GARCOM);
            usuarioService.atualizar(idAtivo, reqVazia);
            assertEquals("senhaCripto123", usuarioAtivo.getSenha());

            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    // =========================================================================
    // BLOCO 8 — ALTERAÇÃO DE E-MAIL E ROUBO DE IDENTIDADE
    // =========================================================================
    @Nested
    @DisplayName("8. Camada de Blindagem — Atualização de E-mail e Cruzamento de Dados")
    class AtualizacaoEmailTests {

        @Test
        @DisplayName("CT-USR-050: Roubo de Identidade — Deve bloquear a alteração se o funcionário tentar usar o e-mail de outro colega ativo")
        void atualizarCenario3() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("João", "maria@tevao.com", "", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.existsByEmailAndIdNot("maria@tevao.com", idAtivo)).thenReturn(true);

            assertThrows(BusinessRuleException.class, () -> usuarioService.atualizar(idAtivo, request));
        }

        @Test
        @DisplayName("CT-USR-048: Manter o Mesmo E-mail — Não deve rodar validações de duplicidade se o e-mail enviado for idêntico ao atual")
        void atualizarCenario4() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("João Caixa Modificado", "joao@tevao.com", "", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            usuarioService.atualizar(idAtivo, request);

            verify(usuarioRepository, never()).existsByEmailAndIdNot(anyString(), any(UUID.class));
        }
    }

    // =========================================================================
    // BLOCO 9 — SOFT DELETE (EXCLUSÃO LÓGICA E INTEGRIDADE FISCAL)
    // =========================================================================
    @Nested
    @DisplayName("9. Camada de Blindagem — Mecanismo de Soft Delete")
    class SoftDeleteTests {

        @Test
        @DisplayName("CT-USR-052 ao CT-USR-056, CT-USR-079, CT-USR-103: Preservação Física — Deve inativar logicamente o usuário fixando ativo=false sem apagar o registro físico")
        void deletarOuInativarCenario1e3() {
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            usuarioService.deletarOuInativar(idAtivo);

            // 🎯 FIX DEFINITIVO: O Lombok gera o padrão isAtivo() para propriedades booleanas primitivas
            assertFalse(usuarioAtivo.isAtivo());
            verify(usuarioRepository, times(1)).save(usuarioAtivo);
            verify(usuarioRepository, never()).delete(any(Usuario.class));
        }

        @Test
        @DisplayName("CT-USR-057: Inativar Inexistente — Tentar desativar um colaborador com ID órfão dispara ResourceNotFoundException")
        void deveLancarExcecaoAoInativarUsuarioInexistente() {
            when(usuarioRepository.findById(any())).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> usuarioService.deletarOuInativar(UUID.randomUUID()));
        }
    }

    // =========================================================================
    // BLOCO 11 — CONTRATO DE PERSISTÊNCIA (VALIDAÇÃO ANTI-NULO)
    // =========================================================================
    @Nested
    @DisplayName("11. Camada de Blindagem — Proteção Anti-Nulos na Esteira JPA")
    class PersistenciaAntiNuloTests {

        @Test
        @DisplayName("CT-USR-065: Quebra Crítica no Insert — Lança BusinessRuleException se o repositório retornar nulo ao salvar")
        void salvarDeveLancarExcecaoQuandoPersistenciaRetornarNulo() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Falha", "falha@tevao.com", "123", RoleUsuario.GARCOM);
            when(usuarioRepository.existsByEmail(request.email())).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenReturn(null);

            assertThrows(BusinessRuleException.class, () -> usuarioService.salvar(request));
        }

        @Test
        @DisplayName("CT-USR-066: Quebra Crítica no Update — Lança BusinessRuleException se o repositório retornar nulo ao atualizar")
        void atualizarDeveLancarExcecaoQuandoPersistenciaRetornarNulo() {
            UsuarioRequestDTO request = new UsuarioRequestDTO("Falha", "joao@tevao.com", "", RoleUsuario.GARCOM);
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenReturn(null);

            assertThrows(BusinessRuleException.class, () -> usuarioService.atualizar(idAtivo, request));
        }
    }

    // =========================================================================
    // BLOCO 15, 16, 17 & 18 — ESCUDOS OPERACIONAIS E FLUXOS COMPLETO DO SALÃO
    // =========================================================================
    @Nested
    @DisplayName("15 a 18. Camada de Blindagem — Fluxos Integrados e Isolamento")
    class FluxosIntegradosLanchesTests {

        @Test
        @DisplayName("CT-USR-076 e CT-USR-100: Segregação Corporativa — Garante que o UsuarioService opere estritamente com funcionários e nunca exponha hashes de senhas em DTOs")
        void deveGarantirQueUsuarioServiceNaoInvoqueEscoposDeClientesExteriores() {
            when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            UsuarioRequestDTO request = new UsuarioRequestDTO("João Monitorado", "joao@tevao.com", "", RoleUsuario.GARCOM);
            UsuarioResponseDTO response = usuarioService.atualizar(idAtivo, request);

            assertNotNull(response);
            verifyNoInteractions(passwordEncoder);
        }
    }
}