package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.ComandaRepository;
import com.paullomaggio.estevaoLanches.repositories.MesaRepository;
import com.paullomaggio.estevaoLanches.services.core.PedidoCoreService;
import jakarta.persistence.EntityManager; // Adicionado
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils; // Adicionado

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GarcomMesaSessaoServiceTest {

    @Mock
    private PedidoCoreService pedidoCoreService;

    @Mock
    private MesaRepository mesaRepository;

    @Mock
    private ComandaRepository comandaRepository;

    @Mock // Adicionado
    private EntityManager entityManager; // Adicionado

    @InjectMocks
    private GarcomMesaSessaoService garcomMesaSessaoService;

    @Captor
    private ArgumentCaptor<PedidoMobileRequestDTO> pedidoCaptor;

    private UUID mesaId;
    private UUID comandaId;
    private Mesa mesa;
    private Comanda comanda;

    @BeforeEach
    void setUp() {
        // MockitoAnnotations.openMocks(this); // Removido: @ExtendWith(MockitoExtension.class) já faz isso
        ReflectionTestUtils.setField(garcomMesaSessaoService, "entityManager", entityManager); // Injeta o mock do EntityManager

        mesaId = UUID.randomUUID();
        comandaId = UUID.randomUUID();

        mesa = new Mesa();
        mesa.setId(mesaId);
        mesa.setNumero(5);
        mesa.setStatus(StatusMesa.OCUPADA);

        comanda = new Comanda();
        comanda.setId(comandaId);
        comanda.setMesa(mesa);
        comanda.setStatus(StatusComanda.ABERTA);
        comanda.setAbertaEm(LocalDateTime.now());
        comanda.setContas(new ArrayList<>());
    }

    // ===================================================================================
    // BLOCO 1 — sincronizarSessao()
    // ===================================================================================

    @Test
    @DisplayName("Teste 001: deve lançar exceção quando Mesa não existir no sincronizar")
    void deveLancarExcecaoQuandoMesaNaoExistir() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.empty());

        GarcomMesaSessaoRequestDTO request = new GarcomMesaSessaoRequestDTO(comandaId, null, List.of());

        assertThrows(ResourceNotFoundException.class, () -> garcomMesaSessaoService.sincronizarSessao(mesaId, request));
        verify(pedidoCoreService, never()).processarPedidoMobile(any());
    }

    @Test
    @DisplayName("Teste 002: deve sincronizar sessão sem enviar pedidos se request não tiver contas")
    void deveSincronizarSessaoSemEnviarPedidos() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoRequestDTO request = new GarcomMesaSessaoRequestDTO(comandaId, null, List.of());
        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.sincronizarSessao(mesaId, request);

        assertNotNull(response);
        verify(pedidoCoreService, never()).processarPedidoMobile(any());
    }

    @Test
    @DisplayName("Teste 003: deve ignorar conta sem novos itens")
    void deveIgnorarContaSemNovosItens() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        // 🎯 FIX: Referenciando as records aninhadas do DTO Pai
        GarcomMesaSessaoRequestDTO.ContaSyncDTO contaSync = new GarcomMesaSessaoRequestDTO.ContaSyncDTO(UUID.randomUUID(), 1, List.of());
        GarcomMesaSessaoRequestDTO request = new GarcomMesaSessaoRequestDTO(comandaId, null, List.of(contaSync));

        garcomMesaSessaoService.sincronizarSessao(mesaId, request);

        verify(pedidoCoreService, never()).processarPedidoMobile(any());
    }

    @Test
    @DisplayName("Teste 004: deve enviar pedido para o PedidoCoreService quando conta tiver novos itens")
    void deveEnviarPedidoParaPedidoCoreService() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoRequestDTO.ItemNovoDTO item1 = new GarcomMesaSessaoRequestDTO.ItemNovoDTO(UUID.randomUUID(), 2, "Sem picles", List.of());
        GarcomMesaSessaoRequestDTO.ItemNovoDTO item2 = new GarcomMesaSessaoRequestDTO.ItemNovoDTO(UUID.randomUUID(), 1, "", List.of());
        GarcomMesaSessaoRequestDTO.ContaSyncDTO contaSync = new GarcomMesaSessaoRequestDTO.ContaSyncDTO(UUID.randomUUID(), 1, List.of(item1, item2));

        GarcomMesaSessaoRequestDTO request = new GarcomMesaSessaoRequestDTO(comandaId, null, List.of(contaSync));

        garcomMesaSessaoService.sincronizarSessao(mesaId, request);

        verify(pedidoCoreService, times(1)).processarPedidoMobile(any(PedidoMobileRequestDTO.class));
    }

    @Test
    @DisplayName("Teste 005: verificar mapeamento correto do DTO enviado para o PDV")
    void verificarMapeamentoCorretoDoDto() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        UUID produtoId = UUID.randomUUID();
        UUID adicionalId = UUID.randomUUID();
        GarcomMesaSessaoRequestDTO.ItemNovoDTO itemNovo = new GarcomMesaSessaoRequestDTO.ItemNovoDTO(produtoId, 3, "Bem passado", List.of(adicionalId));
        GarcomMesaSessaoRequestDTO.ContaSyncDTO contaSync = new GarcomMesaSessaoRequestDTO.ContaSyncDTO(UUID.randomUUID(), 2, List.of(itemNovo));
        GarcomMesaSessaoRequestDTO request = new GarcomMesaSessaoRequestDTO(comandaId, null, List.of(contaSync));

        garcomMesaSessaoService.sincronizarSessao(mesaId, request);

        verify(pedidoCoreService).processarPedidoMobile(pedidoCaptor.capture());
        PedidoMobileRequestDTO payloadEnviado = pedidoCaptor.getValue();

        assertEquals(comandaId, payloadEnviado.comandaId());
        assertEquals(5, payloadEnviado.numeroMesa());
        assertEquals(2, payloadEnviado.numeroConta());
        assertEquals(1, payloadEnviado.itens().size());

        var itemEnviado = payloadEnviado.itens().get(0);
        assertEquals(produtoId, itemEnviado.produtoId());
        assertEquals(3, itemEnviado.quantidade());
        assertEquals("Bem passado", itemEnviado.observacao());
        assertTrue(itemEnviado.adicionaisIds().contains(adicionalId));
    }

    @Test
    @DisplayName("Teste 006: Duas contas (uma com itens, outra sem) deve chamar PDV apenas 1 vez")
    void duasContasUmaSemItensChamaApenasUmaVez() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoRequestDTO.ContaSyncDTO contaComItens = new GarcomMesaSessaoRequestDTO.ContaSyncDTO(UUID.randomUUID(), 1, List.of(new GarcomMesaSessaoRequestDTO.ItemNovoDTO(UUID.randomUUID(), 1, "", List.of())));
        GarcomMesaSessaoRequestDTO.ContaSyncDTO contaSemItens = new GarcomMesaSessaoRequestDTO.ContaSyncDTO(UUID.randomUUID(), 2, List.of());

        GarcomMesaSessaoRequestDTO request = new GarcomMesaSessaoRequestDTO(comandaId, null, List.of(contaComItens, contaSemItens));
        garcomMesaSessaoService.sincronizarSessao(mesaId, request);

        verify(pedidoCoreService, times(1)).processarPedidoMobile(any());
    }

    @Test
    @DisplayName("Teste 007: Duas contas com itens deve chamar PDV duas vezes")
    void duasContasComItensChamaDuasVezes() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoRequestDTO.ContaSyncDTO conta1 = new GarcomMesaSessaoRequestDTO.ContaSyncDTO(UUID.randomUUID(), 1, List.of(new GarcomMesaSessaoRequestDTO.ItemNovoDTO(UUID.randomUUID(), 1, "", List.of())));
        GarcomMesaSessaoRequestDTO.ContaSyncDTO conta2 = new GarcomMesaSessaoRequestDTO.ContaSyncDTO(UUID.randomUUID(), 2, List.of(new GarcomMesaSessaoRequestDTO.ItemNovoDTO(UUID.randomUUID(), 1, "", List.of())));

        GarcomMesaSessaoRequestDTO request = new GarcomMesaSessaoRequestDTO(comandaId, null, List.of(conta1, conta2));
        garcomMesaSessaoService.sincronizarSessao(mesaId, request);

        verify(pedidoCoreService, times(2)).processarPedidoMobile(any());
    }

    // ===================================================================================
    // BLOCO 2 — obterSessao()
    // ===================================================================================

    @Test
    @DisplayName("Teste 008: deve lançar exceção quando Mesa não existir na leitura")
    void deveLancarQuandoMesaNaoExistir() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> garcomMesaSessaoService.obterSessao(mesaId));
    }

    @Test
    @DisplayName("Teste 009: deve lançar exceção quando Mesa não possuir Comanda Ativa")
    void deveLancarQuandoNaoExistirComanda() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> garcomMesaSessaoService.obterSessao(mesaId));
    }

    @Test
    @DisplayName("Teste 010: deve retornar sessão com 0 contas se comanda estiver vazia")
    void deveRetornarSessaoSemContas() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(0, response.contas().size());
        assertNull(response.contaSelecionadaId());
    }

    @Test
    @DisplayName("Teste 011: deve selecionar automaticamente a primeira conta ABERTA (não paga)")
    void selecionaPrimeiraContaAberta() {
        Conta contaPaga = new Conta(); contaPaga.setId(UUID.randomUUID()); contaPaga.setPago(true); contaPaga.setPedidos(List.of());
        Conta contaAberta = new Conta(); contaAberta.setId(UUID.randomUUID()); contaAberta.setPago(false); contaAberta.setPedidos(List.of());
        comanda.setContas(List.of(contaPaga, contaAberta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(contaAberta.getId(), response.contaSelecionadaId());
    }

    @Test
    @DisplayName("Teste 012: se todas estiverem pagas, deve selecionar a primeira da lista")
    void seTodasPagasSelecionaPrimeiraDaLista() {
        Conta contaPaga1 = new Conta(); contaPaga1.setId(UUID.randomUUID()); contaPaga1.setPago(true); contaPaga1.setPedidos(List.of());
        Conta contaPaga2 = new Conta(); contaPaga2.setId(UUID.randomUUID()); contaPaga2.setPago(true); contaPaga2.setPedidos(List.of());
        comanda.setContas(List.of(contaPaga1, contaPaga2));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(contaPaga1.getId(), response.contaSelecionadaId());
    }

    @Test
    @DisplayName("Teste 013: conta sem cliente associado deve retornar null no DTO")
    void contaSemCliente() {
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of()); conta.setCliente(null);
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertNull(response.contas().get(0).cliente());
    }

    @Test
    @DisplayName("Teste 014: conta com cliente deve mapear corretamente id, nome e numero")
    void contaComClienteMapeado() {
        Cliente cliente = new Cliente(); cliente.setId(UUID.randomUUID()); cliente.setNome("João"); cliente.setNumero("1199999999");
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of());
        // 🎯 FIX: Setar nomeResponsavel e telefoneResponsavel diretamente na conta
        conta.setNomeResponsavel(cliente.getNome());
        conta.setTelefoneResponsavel(cliente.getNumero());
        conta.setCliente(cliente); // Manter para compatibilidade, embora não seja mais a fonte primária para ClienteSessaoDTO

        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        var clienteSessao = response.contas().get(0).cliente();
        assertNotNull(clienteSessao);
        // 🎯 FIX: Assertions agora verificam os campos nomeResponsavel e telefoneResponsavel da Conta
        assertEquals("João", clienteSessao.nome());
        assertEquals("1199999999", clienteSessao.telefone());
        // O ID do cliente na sessão agora é o ID da conta, não o ID do cliente da entidade Cliente
        assertEquals(conta.getId(), clienteSessao.id());
    }

    @Test
    @DisplayName("Teste 015: pedidos CANCELADOS devem ter seus itens ignorados")
    void ignorarPedidosCancelados() {
        Pedido pedidoCancelado = new Pedido(); pedidoCancelado.setStatus(StatusPedido.CANCELADO);
        ItemPedido itemCancelado = mockItem(BigDecimal.TEN, 1, false);
        pedidoCancelado.setItens(List.of(itemCancelado));

        Pedido pedidoAtivo = new Pedido(); pedidoAtivo.setStatus(StatusPedido.EM_PREPARO);
        ItemPedido itemAtivo = mockItem(BigDecimal.TEN, 1, false);
        pedidoAtivo.setItens(List.of(itemAtivo));

        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedidoCancelado, pedidoAtivo));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(1, response.contas().get(0).itens().size());
        assertEquals(itemAtivo.getId(), response.contas().get(0).itens().get(0).id());
    }

    @Test
    @DisplayName("Teste 016: deve calcular valor total da conta (Hamburguer 2x20 + Batata 1x10 = 50)")
    void calcularValorTotalDaConta() {
        ItemPedido hamburguer = mockItem(BigDecimal.valueOf(20), 2, false);
        ItemPedido batata = mockItem(BigDecimal.valueOf(10), 1, false);

        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setItens(List.of(hamburguer, batata));
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(0, BigDecimal.valueOf(50).compareTo(response.contas().get(0).valorTotal()));
    }

    @Test
    @DisplayName("Teste 017: valor unitário do item deve vir da tabela do Produto")
    void valorUnitarioDeveVirDoProduto() {
        ItemPedido item = mockItem(BigDecimal.valueOf(25.5), 1, false);
        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO);
        pedido.setItens(List.of(item));
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(BigDecimal.valueOf(25.5), response.contas().get(0).itens().get(0).valorUnitario());
    }

    @Test
    @DisplayName("Teste 018: valor total do item deve ser precoUnitario x quantidade")
    void valorTotalItemMultiplicaQuantidade() {
        ItemPedido item = mockItem(BigDecimal.valueOf(15), 3, false);
        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO); pedido.setItens(List.of(item));
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(0, BigDecimal.valueOf(45).compareTo(response.contas().get(0).itens().get(0).valorTotal()));
    }

    @Test
    @DisplayName("Teste 019: deve mapear os adicionais do item")
    void mapearAdicionais() {
        ItemPedido item = mockItem(BigDecimal.TEN, 1, true);
        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO); pedido.setItens(List.of(item));
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(2, response.contas().get(0).itens().get(0).adicionais().size());
    }

    @Test
    @DisplayName("Teste 020: Produto precisaPreparo=true deve refletir no item")
    void precisaPreparoVerdadeiro() {
        ItemPedido item = mockItem(BigDecimal.TEN, 1, false);
        item.getProduto().setPrecisaPreparo(true);
        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO); pedido.setItens(List.of(item));
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertTrue(response.contas().get(0).itens().get(0).precisaPreparo());
    }

    @Test
    @DisplayName("Teste 021: Produto precisaPreparo=false deve refletir no item")
    void precisaPreparoFalso() {
        ItemPedido item = mockItem(BigDecimal.TEN, 1, false);
        item.getProduto().setPrecisaPreparo(false);
        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO); pedido.setItens(List.of(item));
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertFalse(response.contas().get(0).itens().get(0).precisaPreparo());
    }

    @Test
    @DisplayName("Teste 022: status da conta (Paga -> PAGO, Aberta -> ABERTO)")
    void statusDaContaMapeadoPeloEnum() {
        Conta conta1 = new Conta(); conta1.setId(UUID.randomUUID()); conta1.setPago(true); conta1.setPedidos(List.of());
        Conta conta2 = new Conta(); conta2.setId(UUID.randomUUID()); conta2.setPago(false); conta2.setPedidos(List.of());
        comanda.setContas(List.of(conta1, conta2));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(StatusPagamento.PAGO, response.contas().get(0).statusConta());
        assertEquals(StatusPagamento.ABERTO, response.contas().get(1).statusConta());
    }

    @Test
    @DisplayName("Teste 023: Todos os itens vindos do banco devem estar sinalizados como enviado=true (cinza)")
    void itensVindosDoBancoSaoEnviados() {
        ItemPedido item = mockItem(BigDecimal.TEN, 1, false);
        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO); pedido.setItens(List.of(item));
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertTrue(response.contas().get(0).itens().get(0).enviado());
    }

    @Test
    @DisplayName("Teste 024: Retornar corretamente propriedades da raiz (mesa, comanda, datas)")
    void retornarPropriedadesDaRaiz() {
        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(mesaId, response.mesaId());
        assertEquals(5, response.numeroMesa());
        assertEquals(StatusMesa.OCUPADA, response.statusMesa());
        assertEquals(comandaId, response.comandaId());
        assertEquals(StatusComanda.ABERTA, response.statusComanda());
        assertNotNull(response.abertaEm());
    }

    // ===================================================================================
    // BLOCO 3 — Casos Extremos
    // ===================================================================================

    @Test
    @DisplayName("Teste 025: Conta sem pedidos mapeia para lista de itens vazia []")
    void contaSemPedidosTemItensVazios() {
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(null);
        conta.setPedidos(List.of());
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertTrue(response.contas().get(0).itens().isEmpty());
    }

    @Test
    @DisplayName("Teste 026: Pedido sem itens resulta em conta com valorTotal = 0")
    void pedidoSemItensValorZerado() {
        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO); pedido.setItens(List.of());
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(0, BigDecimal.ZERO.compareTo(response.contas().get(0).valorTotal()));
    }

    @Test
    @DisplayName("Teste 027: Item sem adicionais retorna array vazio []")
    void itemSemAdicionaisRetornaListaVazia() {
        ItemPedido item = mockItem(BigDecimal.TEN, 1, false);
        item.setAdicionais(List.of());
        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO); pedido.setItens(List.of(item));
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertTrue(response.contas().get(0).itens().get(0).adicionais().isEmpty());
    }

    @Test
    @DisplayName("Teste 028: Múltiplas contas são mapeadas integralmente")
    void variasContasMapeadas() {
        List<Conta> contasGeradas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Conta c = new Conta(); c.setId(UUID.randomUUID()); c.setPedidos(List.of());
            contasGeradas.add(c);
        }
        comanda.setContas(contasGeradas);

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(5, response.contas().size());
    }

    @Test
    @DisplayName("Teste 029: Pedido com vários itens, todos aparecem na árvore achatada")
    void pedidoComVariosItensAparecemTodos() {
        ItemPedido item1 = mockItem(BigDecimal.TEN, 1, false);
        ItemPedido item2 = mockItem(BigDecimal.TEN, 1, false);
        ItemPedido item3 = mockItem(BigDecimal.TEN, 1, false);
        Pedido pedido = new Pedido(); pedido.setStatus(StatusPedido.EM_PREPARO); pedido.setItens(List.of(item1, item2, item3));
        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(pedido));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);
        assertEquals(3, response.contas().get(0).itens().size());
    }

    @Test
    @DisplayName("Teste 030: Somatório de valor total consolidando vários pedidos da mesma conta")
    void somatorioConsolidadoVariosPedidosNaConta() {
        ItemPedido i1 = mockItem(BigDecimal.valueOf(10), 1, false);
        Pedido p1 = new Pedido(); p1.setStatus(StatusPedido.EM_PREPARO); p1.setItens(List.of(i1));

        ItemPedido i2 = mockItem(BigDecimal.valueOf(20), 2, false);
        Pedido p2 = new Pedido(); p2.setStatus(StatusPedido.EM_PREPARO); p2.setItens(List.of(i2));

        ItemPedido i3 = mockItem(BigDecimal.valueOf(50), 1, false);
        Pedido p3 = new Pedido(); p3.setStatus(StatusPedido.EM_PREPARO); p3.setItens(List.of(i3));

        Conta conta = new Conta(); conta.setId(UUID.randomUUID()); conta.setPedidos(List.of(p1, p2, p3));
        comanda.setContas(List.of(conta));

        when(mesaRepository.findById(mesaId)).thenReturn(Optional.of(mesa));
        when(comandaRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusComanda.ABERTA)).thenReturn(Optional.of(comanda));

        GarcomMesaSessaoResponseDTO response = garcomMesaSessaoService.obterSessao(mesaId);

        // 10 + 40 + 50 = 100
        assertEquals(0, BigDecimal.valueOf(100).compareTo(response.contas().get(0).valorTotal()));
    }

    // ===================================================================================
    // HELPER METHODS
    // ===================================================================================

    private ItemPedido mockItem(BigDecimal precoUnitarioBase, int quantidade, boolean comAdicionais) {
        Produto produto = new Produto();
        produto.setId(UUID.randomUUID());
        produto.setNome("Produto Teste");
        produto.setPreco(precoUnitarioBase);
        produto.setPrecisaPreparo(true);

        ItemPedido item = new ItemPedido();
        item.setId(UUID.randomUUID());
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setPrecoUnitario(precoUnitarioBase);
        item.setObservacaoItem("");

        if (comAdicionais) {
            Adicional a1 = new Adicional(); a1.setId(UUID.randomUUID()); a1.setNome("Bacon"); a1.setPreco(BigDecimal.valueOf(2));
            Adicional a2 = new Adicional(); a2.setId(UUID.randomUUID()); a2.setNome("Queijo"); a2.setPreco(BigDecimal.valueOf(3));
            item.setAdicionais(List.of(a1, a2));
        } else {
            item.setAdicionais(List.of());
        }

        return item;
    }
}