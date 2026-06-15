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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuarioAtivo;
    private Usuario usuarioInativo;
    private UUID idAtivo;
    private UUID idInativo;

    @BeforeEach
    void setUp() {
        idAtivo = UUID.randomUUID();
        idInativo = UUID.randomUUID();

        usuarioAtivo = new Usuario(idAtivo, "João Caixa", "joao@tevao.com", "senhaCripto123", RoleUsuario.GARCOM, true);
        usuarioInativo = new Usuario(idInativo, "Maria Antiga", "maria@tevao.com", "senhaCripto456", RoleUsuario.COZINHA, false);
    }

    // ==========================================
    // CONTROLES DE CONTRATO DO SPRING SECURITY
    // ==========================================

    @Test
    @DisplayName("Deve carregar UserDetails com sucesso ao buscar email corporativo existente")
    void loadUserByUsernameCenario1() {
        when(usuarioRepository.findByEmail("joao@tevao.com")).thenReturn(Optional.of(usuarioAtivo));

        UserDetails resultado = usuarioService.loadUserByUsername("joao@tevao.com");

        assertNotNull(resultado);
        assertEquals("joao@tevao.com", resultado.getUsername());
        verify(usuarioRepository, times(1)).findByEmail("joao@tevao.com");
    }

    @Test
    @DisplayName("Deve estourar UsernameNotFoundException quando o email informado não existir no Postgres")
    void loadUserByUsernameCenario2() {
        when(usuarioRepository.findByEmail("fantasma@tevao.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> usuarioService.loadUserByUsername("fantasma@tevao.com"));
    }

    // ==========================================
    // 1. TESTES DE LISTAGEM GERAL
    // ==========================================

    @Test
    @DisplayName("Deve retornar todos os usuários convertidos para DTO")
    void listarTodosCenario1() {
        when(usuarioRepository.findAll()).thenReturn(List.of(usuarioAtivo, usuarioInativo));

        List<UsuarioResponseDTO> resultado = usuarioService.listarTodos();

        assertEquals(2, resultado.size());
        assertEquals("João Caixa", resultado.get(0).nome());
        assertEquals("Maria Antiga", resultado.get(1).nome());
    }

    @Test
    @DisplayName("Deve retornar lista vazia se não houver usuários no banco")
    void listarTodosCenario2() {
        when(usuarioRepository.findAll()).thenReturn(Collections.emptyList());

        List<UsuarioResponseDTO> resultado = usuarioService.listarTodos();

        assertTrue(resultado.isEmpty());
    }

    // ==========================================
    // 2. TESTES DE FILTRO DE USUÁRIOS ATIVOS
    // ==========================================

    @Test
    @DisplayName("Deve retornar somente colaboradores ativos")
    void listarApenasAtivosCenario1() {
        when(usuarioRepository.findByAtivoTrue()).thenReturn(List.of(usuarioAtivo));

        List<UsuarioResponseDTO> resultado = usuarioService.listarApenasAtivos();

        assertEquals(1, resultado.size());
        assertEquals("João Caixa", resultado.get(0).nome());
        assertTrue(resultado.get(0).ativo());
    }

    @Test
    @DisplayName("Deve retornar lista vazia caso todos estejam inativos")
    void listarApenasAtivosCenario2() {
        when(usuarioRepository.findByAtivoTrue()).thenReturn(Collections.emptyList());

        List<UsuarioResponseDTO> resultado = usuarioService.listarApenasAtivos();

        assertTrue(resultado.isEmpty());
    }

    // ==========================================
    // 3. TESTES DE BUSCA POR ID
    // ==========================================

    @Test
    @DisplayName("Deve localizar usuário existente e retornar DTO")
    void buscarPorIdCenario1() {
        when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));

        UsuarioResponseDTO resultado = usuarioService.buscarPorId(idAtivo);

        assertNotNull(resultado);
        assertEquals(idAtivo, resultado.id());
    }

    @Test
    @DisplayName("Deve estourar ResourceNotFoundException para ID inexistente")
    void buscarPorIdCenario2() {
        when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> usuarioService.buscarPorId(UUID.randomUUID()));
    }

    // ==========================================
    // 4. TESTES DE SALVAMENTO E CRIPTOGRAFIA (BCRYPT)
    // ==========================================

    @Test
    @DisplayName("Deve persistir usuário com sucesso se o e-mail for inédito")
    void salvarCenario1() {
        UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "paulo@tevao.com", "senha123", RoleUsuario.ADMIN);
        when(usuarioRepository.existsByEmail(request.email())).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponseDTO resultado = usuarioService.salvar(request);

        assertNotNull(resultado);
        assertEquals("Paulo", resultado.nome());
        assertTrue(resultado.ativo());
    }

    @Test
    @DisplayName("Deve estourar BusinessRuleException caso o e-mail já exista")
    void salvarCenario2() {
        UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "joao@tevao.com", "senha123", RoleUsuario.ADMIN);
        when(usuarioRepository.existsByEmail(request.email())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> usuarioService.salvar(request));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("CRÍTICO: Deve acionar o PasswordEncoder para mascarar a senha se ele estiver configurado")
    void salvarCenario3() {
        UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "paulo@tevao.com", "textoPuro123", RoleUsuario.ADMIN);
        when(usuarioRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode("textoPuro123")).thenReturn("hashBCrypt8821");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        usuarioService.salvar(request);

        verify(passwordEncoder, times(1)).encode("textoPuro123");
    }

    @Test
    @DisplayName("Deve manter a senha em texto puro se o PasswordEncoder estiver nulo (Ambiente de Testes Limpo)")
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

    @Test
    @DisplayName("Deve associar o cargo (RoleUsuario) exatamente conforme enviado no DTO")
    void salvarCenario5() {
        UsuarioRequestDTO request = new UsuarioRequestDTO("Paulo", "paulo@tevao.com", "senha1", RoleUsuario.ADMIN);
        when(usuarioRepository.existsByEmail(request.email())).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponseDTO resultado = usuarioService.salvar(request);

        assertEquals(RoleUsuario.ADMIN, resultado.role());
    }

    // ==========================================
    // 5. TESTES DE ATUALIZAÇÃO DE CADASTROS
    // ==========================================

    @Test
    @DisplayName("Deve modificar dados cadastrais com sucesso em cenário normal")
    void atualizarCenario1() {
        UsuarioRequestDTO request = new UsuarioRequestDTO("João Alterado", "joao@tevao.com", "", RoleUsuario.GARCOM);
        when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO resultado = usuarioService.atualizar(idAtivo, request);

        assertEquals("João Alterado", resultado.nome());
    }

    @Test
    @DisplayName("Deve estourar ResourceNotFoundException ao tentar modificar ID fantasma")
    void atualizarCenario2() {
        UsuarioRequestDTO request = new UsuarioRequestDTO("Nome", "email@email.com", "", RoleUsuario.GARCOM);
        when(usuarioRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> usuarioService.atualizar(UUID.randomUUID(), request));
    }

    @Test
    @DisplayName("CRÍTICO: Deve bloquear roubo de e-mail se funcionário tentar mudar seu cadastro para o e-mail de outro colega")
    void atualizarCenario3() {
        UsuarioRequestDTO request = new UsuarioRequestDTO("João", "maria@tevao.com", "", RoleUsuario.GARCOM);
        when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
        when(usuarioRepository.existsByEmail("maria@tevao.com")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> usuarioService.atualizar(idAtivo, request));
    }

    @Test
    @DisplayName("Não deve validar duplicidade se o e-mail enviado for idêntico ao e-mail que o usuário já possui")
    void atualizarCenario4() {
        UsuarioRequestDTO request = new UsuarioRequestDTO("João Caixa Modificado", "joao@tevao.com", "", RoleUsuario.GARCOM);
        when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        usuarioService.atualizar(idAtivo, request);

        verify(usuarioRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("Deve atualizar e codificar nova senha caso ela seja alterada no formulário")
    void atualizarCenario5() {
        UsuarioRequestDTO request = new UsuarioRequestDTO("João", "joao@tevao.com", "novaSenhaMaster", RoleUsuario.GARCOM);
        when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
        when(passwordEncoder.encode("novaSenhaMaster")).thenReturn("novaSenhaCripto999");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        usuarioService.atualizar(idAtivo, request);

        verify(passwordEncoder, times(1)).encode("novaSenhaMaster");
    }

    @Test
    @DisplayName("Deve preservar a senha anterior intacta se a nova senha vier Nula, Vazia ou em Branco")
    void atualizarCenariosSenhaNulaEVazia() {
        when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioRequestDTO reqNull = new UsuarioRequestDTO("João", "joao@tevao.com", null, RoleUsuario.GARCOM);
        usuarioService.atualizar(idAtivo, reqNull);
        assertEquals("senhaCripto123", usuarioAtivo.getSenha());

        UsuarioRequestDTO reqVazia = new UsuarioRequestDTO("João", "joao@tevao.com", "", RoleUsuario.GARCOM);
        usuarioService.atualizar(idAtivo, reqVazia);
        assertEquals("senhaCripto123", usuarioAtivo.getSenha());

        UsuarioRequestDTO reqBranco = new UsuarioRequestDTO("João", "joao@tevao.com", " ", RoleUsuario.GARCOM);
        usuarioService.atualizar(idAtivo, reqBranco);
        assertEquals("senhaCripto123", usuarioAtivo.getSenha());

        verify(passwordEncoder, never()).encode(anyString());
    }

    // ==========================================
    // 6. TESTES DE EXCLUSÃO LÓGICA (SOFT DELETE)
    // ==========================================

    @Test
    @DisplayName("CRÍTICO: Deve apenas inativar o colaborador (Soft Delete) para manter a integridade fiscal das comandas vendidas")
    void deletarOuInativarCenario1e3() {
        when(usuarioRepository.findById(idAtivo)).thenReturn(Optional.of(usuarioAtivo));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        usuarioService.deletarOuInativar(idAtivo);

        assertFalse(usuarioAtivo.getAtivo());
        verify(usuarioRepository, times(1)).save(usuarioAtivo);
        verify(usuarioRepository, never()).delete(any(Usuario.class));
        verify(usuarioRepository, never()).deleteById(any(UUID.class));
    }
}