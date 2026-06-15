package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CaixaAberturaRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaFechamentoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaResumoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.CaixaStatusResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Caixa;
import com.paullomaggio.estevaoLanches.entities.Usuario;
import com.paullomaggio.estevaoLanches.enums.RoleUsuario;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaixaServiceTest {

    @Mock
    private CaixaRepository caixaRepository;

    @Mock
    private PedidoRepository pedidoRepository; // 🚨 INJETADO PARA OS NOVOS TESTES DE KPI

    @InjectMocks
    private CaixaService caixaService;

    private Usuario gerente;
    private Usuario operador;
    private Caixa caixaAbertoMock;

    @BeforeEach
    void setUp() {
        gerente = new Usuario(UUID.randomUUID(), "Estêvão Dono", "admin@estevaolanches.com", "123", RoleUsuario.ADMIN, true);
        operador = new Usuario(UUID.randomUUID(), "João Caixa", "caixa@estevaolanches.com", "123", RoleUsuario.GARCOM, true);

        caixaAbertoMock = new Caixa(UUID.randomUUID(), LocalDateTime.now().minusHours(2), null, StatusCaixa.ABERTO, BigDecimal.valueOf(100), null, gerente, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockarUsuarioLogado(Usuario usuario) {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(usuario);
        SecurityContextHolder.setContext(securityContext);
    }

    // ==========================================
    // GRUPO 1 - obterStatusAtual()
    // ==========================================

    @Test
    @DisplayName("CT-010 - Deve retornar caixa aberto atual")
    void obterStatusAtualCenario1() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoMock));

        Optional<CaixaStatusResponseDTO> resultado = caixaService.obterStatusAtual();

        assertTrue(resultado.isPresent());
        assertTrue(resultado.get().aberto());
        assertEquals("Estêvão Dono", resultado.get().nomeUsuarioAbertura());
    }

    @Test
    @DisplayName("CT-011 - Deve retornar vazio quando não houver caixa aberto")
    void obterStatusAtualCenario2() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

        Optional<CaixaStatusResponseDTO> resultado = caixaService.obterStatusAtual();

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("CT-EXTRA - Deve manter o status de Caixa Aberto para o Turno de forma global independente de qual funcionário consultar")
    void obterStatusAtualCenarioTurnoGlobal() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoMock));

        mockarUsuarioLogado(operador);
        Optional<CaixaStatusResponseDTO> resultadoParaOperador = caixaService.obterStatusAtual();

        assertTrue(resultadoParaOperador.isPresent());
        assertTrue(resultadoParaOperador.get().aberto());
        assertEquals("Estêvão Dono", resultadoParaOperador.get().nomeUsuarioAbertura());
    }

    // ==========================================
    // GRUPO 2 & 4 - abrirCaixa() & Segurança
    // ==========================================

    @Test
    @DisplayName("CT-012/13/14/15/18/19 - Deve abrir caixa com sucesso injetando dados do SecurityContext")
    void abrirCaixaFluxoFeliz() {
        mockarUsuarioLogado(gerente);
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(false);
        when(caixaRepository.save(any(Caixa.class))).thenAnswer(i -> i.getArgument(0));

        CaixaAberturaRequestDTO dto = new CaixaAberturaRequestDTO(BigDecimal.valueOf(150));
        CaixaStatusResponseDTO resultado = caixaService.abrirCaixa(dto);

        assertNotNull(resultado);
        assertTrue(resultado.aberto());
        assertEquals(BigDecimal.valueOf(150), resultado.valorAbertura());
        assertEquals("Estêvão Dono", resultado.nomeUsuarioAbertura());
        verify(caixaRepository, times(1)).save(any(Caixa.class));
    }

    @Test
    @DisplayName("CT-016/17 - Não deve permitir abrir segundo caixa se já houver um ativo")
    void abrirCaixaCenarioDuplicado() {
        when(caixaRepository.existsByStatus(StatusCaixa.ABERTO)).thenReturn(true);

        CaixaAberturaRequestDTO dto = new CaixaAberturaRequestDTO(BigDecimal.TEN);

        assertThrows(BusinessRuleException.class, () -> caixaService.abrirCaixa(dto));
        verify(caixaRepository, never()).save(any(Caixa.class));
    }

    // ==========================================
    // GRUPO 5 & 7 - fecharCaixa() & Segurança
    // ==========================================

    @Test
    @DisplayName("CT-020/21/22/23/24/27/29/30/31/32/33/34 - Deve fechar caixa salvando carimbo do funcionário de encerramento")
    void fecharCaixaFluxoFeliz() {
        mockarUsuarioLogado(operador);
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoMock));

        CaixaFechamentoRequestDTO dto = new CaixaFechamentoRequestDTO(BigDecimal.valueOf(500));

        caixaService.fecharCaixa(dto);

        assertEquals(StatusCaixa.FECHADO, caixaAbertoMock.getStatus());
        assertEquals(BigDecimal.valueOf(500), caixaAbertoMock.getValorFechamento());
        assertNotNull(caixaAbertoMock.getDataHoraFechamento());
        assertEquals(operador, caixaAbertoMock.getUsuarioFechamento());
        assertEquals(gerente, caixaAbertoMock.getUsuarioAbertura());
        assertEquals(BigDecimal.valueOf(100), caixaAbertoMock.getValorAbertura());

        verify(caixaRepository, times(1)).save(caixaAbertoMock);
    }

    @Test
    @DisplayName("CT-025/26 - Deve estourar exceção se tentar fechar um caixa quando todos já estão fechados")
    void fecharCaixaCenarioInexistente() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

        CaixaFechamentoRequestDTO dto = new CaixaFechamentoRequestDTO(BigDecimal.TEN);

        assertThrows(BusinessRuleException.class, () -> caixaService.fecharCaixa(dto));
        verify(caixaRepository, never()).save(any(Caixa.class));
    }

    // ==========================================
    // 🚀 NOVOS CENÁRIOS: GRUPO DE RESUMO FINANCEIRO (KPIs DO TURNO)
    // ==========================================

    @Test
    @DisplayName("CT-SERVICE-RESUMO-001: Deve processar e calcular com precisão a matemática financeira do turno")
    void deveCalcularResumoDoTurnoComSucesso() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.of(caixaAbertoMock));

        // Simulação do comportamento de agregação do repositório de pedidos
        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), eq("DINHEIRO"), eq(StatusPedido.FINALIZADO))).thenReturn(new BigDecimal("100.00"));
        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), eq("PIX"), eq(StatusPedido.FINALIZADO))).thenReturn(new BigDecimal("150.00"));
        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), eq("CREDITO"), eq(StatusPedido.FINALIZADO))).thenReturn(new BigDecimal("50.00"));
        when(pedidoRepository.somarFaturamentoPorTurnoEForma(any(), eq("DEBITO"), eq(StatusPedido.FINALIZADO))).thenReturn(BigDecimal.ZERO);
        when(pedidoRepository.countPedidosAtivos(StatusPedido.FINALIZADO, StatusPedido.CANCELADO)).thenReturn(5L);

        CaixaResumoResponseDTO resumo = caixaService.obterResumoTurno();

        assertNotNull(resumo);
        assertEquals(0, new BigDecimal("300.00").compareTo(resumo.faturamentoTotal())); // 100 + 150 + 50
        assertEquals(0, new BigDecimal("100.00").compareTo(resumo.faturamentoDinheiro()));
        assertEquals(0, new BigDecimal("150.00").compareTo(resumo.faturamentoPix()));

        // Fundo Inicial (100.00) + Vendas em espécie (100.00) = Gaveta deve esperar 200.00
        assertEquals(0, new BigDecimal("200.00").compareTo(resumo.totalEsperadoGaveta()));
        assertEquals(5, resumo.pedidosEmEsteira());
    }

    @Test
    @DisplayName("CT-SERVICE-RESUMO-002: Deve barrar solicitação de resumo se o estabelecimento estiver fechado")
    void deveNegarResumoSeCaixaFechado() {
        when(caixaRepository.findByStatus(StatusCaixa.ABERTO)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> caixaService.obterResumoTurno());
        verify(pedidoRepository, never()).somarFaturamentoPorTurnoEForma(any(), any(), any());
    }
}