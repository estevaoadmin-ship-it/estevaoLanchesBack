package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    private static final String UNIQUE_CONSTRAINT_CONTA_NUMERO = "uk_comanda_id_numero_conta";
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    private final PedidoRepository pedidoRepository;
    private final CarrinhoRepository carrinhoRepository;
    private final CaixaRepository caixaRepository;
    private final ProdutoRepository produtoRepository;
    private final AdicionalRepository adicionalRepository;
    private final FilaImpressaoRepository filaImpressaoRepository;
    private final ComandaRepository comandaRepository;
    private final ContaRepository contaRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ItemComboRepository itemComboRepository;
    private final ComboProdutoRepository comboProdutoRepository;
    private final PagamentoService pagamentoService;
    private final AdicionalValidationService adicionalValidationService;
    private final ClienteRepository clienteRepository;

    // Record temporário para correlacionar o DTO de entrada com o ItemPedido criado (PedidoMobile)
    private record ItemPedidoCorrelation(
            PedidoMobileRequestDTO.ItemPedidoPayloadDTO payloadDTO,
            ItemPedido itemPedido
    ) {}

    // Record temporário para correlacionar o DTO de entrada com o ItemPedido criado (CheckoutDelivery explícito)
    private record CheckoutDeliveryItemCorrelation(
            CheckoutDeliveryItemRequestDTO payloadDTO,
            ItemPedido itemPedido
    ) {}

    // Record temporário para correlacionar ItemCarrinho com ItemPedido criado (Checkout Delivery legado)
    private record ItemCarrinhoToItemPedidoCorrelation(
            ItemCarrinho itemCarrinho,
            ItemPedido itemPedido
    ) {}

    @Transactional
    public PedidoResponseDTO processarPedidoMobile(PedidoMobileRequestDTO dto) {
        if (!caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Operação negada: O turno do caixa está fechado.");
        }

        Optional<Conta> contaExistente =
                contaRepository.findByComandaIdAndNumeroConta(dto.comandaId(), dto.numeroConta());

        Conta conta = contaExistente.orElseGet(() ->
                getOrCreateAccountWithConcurrencyProtection(
                        dto.comandaId(),
                        dto.numeroConta()
                )
        );

        if (conta.getPago()) {
            throw new BusinessRuleException("Bloqueio comercial: Esta subconta já foi encerrada e paga no caixa.");
        }

        Pedido pedido = new Pedido();
        pedido.setConta(conta);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setNumeroMesa(dto.numeroMesa());
        pedido.setTotal(BigDecimal.ZERO);
        pedido.setItens(new ArrayList<>());

        if (dto.cliente() != null && dto.cliente().nome() != null) {
            pedido.setNomeClienteBalcao(dto.cliente().nome().toUpperCase().trim());
        }

        if (pedido.getNomeClienteBalcao() == null || pedido.getNomeClienteBalcao().isBlank()) {
            vincularResponsavelMesa(pedido, conta);
        }

        BigDecimal subtotalLote = BigDecimal.ZERO;
        boolean necessitaPreparoCozinha = false;
        List<ItemPedidoCorrelation> correlations = new ArrayList<>(); // Lista para correlação

        for (PedidoMobileRequestDTO.ItemPedidoPayloadDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findById(itemDto.produtoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto do cardápio não localizado."));

            List<Adicional> adicionaisVinculados = itemDto.adicionaisIds() != null ?
                    adicionalRepository.findAllById(itemDto.adicionaisIds()) : new ArrayList<>();

            BigDecimal precoAdicionais = adicionaisVinculados.stream()
                    .map(Adicional::getPreco)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal precoFinalItemUnitario = produto.getPreco().add(precoAdicionais);
            if (precoFinalItemUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException("Preço final do item não pode ser zero ou negativo.");
            }

            ItemPedido item = new ItemPedido();
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(precoFinalItemUnitario);
            item.setAdicionais(adicionaisVinculados);
            item.setPedido(pedido);
            item.setObservacaoItem(itemDto.observacao());
            item.setNumeroConta(dto.numeroConta());
            item.setStatusPagamento(StatusPagamento.ABERTO);

            pedido.getItens().add(item);
            subtotalLote = subtotalLote.add(precoFinalItemUnitario.multiply(BigDecimal.valueOf(itemDto.quantidade())));

            if (produto.getPrecisaPreparo()) {
                necessitaPreparoCozinha = true;
            }
            correlations.add(new ItemPedidoCorrelation(itemDto, item)); // Adiciona a correlação
        }

        pedido.setTotal(subtotalLote);
        Pedido pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        Map<UUID, Map<UUID, ItemCombo>> comboSnapshotsCriados = criarSnapshotsDosCombos(pedidoSalvo);

        // Apply customizations using the direct correlation
        for (ItemPedidoCorrelation correlation : correlations) {
            PedidoMobileRequestDTO.ItemPedidoPayloadDTO itemDto = correlation.payloadDTO();
            ItemPedido itemPedido = correlation.itemPedido();

            if (itemDto.itensCombo() != null && !itemDto.itensCombo().isEmpty()) {
                Map<UUID, ItemCombo> itemComboMap = comboSnapshotsCriados.get(itemPedido.getId());
                if (itemComboMap == null || itemComboMap.isEmpty()) {
                    throw new BusinessRuleException("Snapshots de combo não encontrados para o ItemPedido " + itemPedido.getId());
                }

                aplicarCustomizacoesDosItensCombo(itemPedido, itemComboMap, itemDto.itensCombo());
            }
        }

        // Recalculate total after applying combo customizations
        recalcularTotalPedido(pedidoSalvo.getId());


        if (necessitaPreparoCozinha) {
            FilaImpressao cupomCozinha = new FilaImpressao();
            cupomCozinha.setPedido(pedidoSalvo);
            cupomCozinha.setDestino(FilaImpressao.DestinoImpressao.COZINHA);
            cupomCozinha.setStatus(FilaImpressao.StatusImpressao.PENDENTE);
            filaImpressaoRepository.save(cupomCozinha);
        }

        PedidoResponseDTO responseDTO = new PedidoResponseDTO(pedidoSalvo);
        messagingTemplate.convertAndSend("/topic/caixa", responseDTO);
        messagingTemplate.convertAndSend("/topic/cozinha", responseDTO);

        return responseDTO;
    }

    private Conta getOrCreateAccountWithConcurrencyProtection(UUID comandaId, Integer numeroConta) {
        Comanda comandaMestre = comandaRepository.findById(comandaId)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão de comanda mestre não localizada."));

        Conta novaConta = new Conta();
        novaConta.setComanda(comandaMestre);
        novaConta.setNumeroConta(numeroConta);
        novaConta.setValorTotal(BigDecimal.ZERO);
        novaConta.setPago(false);

        try {
            log.info("[CONCORRENCIA] Thread {} - Tentando salvar nova Conta {} para Comanda {}", Thread.currentThread().getName(), numeroConta, comandaId);
            Conta contaSalva = contaRepository.saveAndFlush(novaConta);
            return contaSalva;
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e, UNIQUE_CONSTRAINT_CONTA_NUMERO)) {
                log.warn("[CONCORRENCIA] Thread {} - Conflito detectado ({}). Re-buscando Conta existente. Comanda ID: {}, Numero Conta: {}. Erro: {}",
                        Thread.currentThread().getName(), UNIQUE_CONSTRAINT_CONTA_NUMERO, comandaId, numeroConta, e.getMessage());

                Conta contaRecuperada = contaRepository.findByComandaIdAndNumeroConta(comandaId, numeroConta)
                        .orElseThrow(() -> {
                            log.error("[CONCORRENCIA] Thread {} - Erro grave: Conta não encontrada após conflito de concorrência esperado. Comanda ID: {}, Numero Conta: {}", Thread.currentThread().getName(), comandaId, numeroConta);
                            return new BusinessRuleException("Erro de concorrência: Conta deveria existir, mas não foi encontrada após conflito. Comanda ID: " + comandaId + ", Numero Conta: " + numeroConta);
                        });

                if (!contaRecuperada.getNumeroConta().equals(numeroConta) || !contaRecuperada.getComanda().getId().equals(comandaId)) {
                    log.error("[CONCORRENCIA] Thread {} - Erro grave: Conta recuperada após conflito não corresponde aos dados esperados. Esperado Comanda ID: {}, Numero Conta: {}. Encontrado Comanda ID: {}, Numero Conta: {}",
                            Thread.currentThread().getName(), comandaId, numeroConta, contaRecuperada.getComanda().getId(), contaRecuperada.getNumeroConta());
                    throw new BusinessRuleException("Erro de concorrência: Conta recuperada após conflito não corresponde aos dados esperados. Comanda ID: " + comandaId + ", Numero Conta: " + numeroConta);
                }
                log.info("[CONCORRENCIA] Thread {} - Conta {} para Comanda {} recuperada com sucesso após conflito.", Thread.currentThread().getName(), contaRecuperada.getNumeroConta(), contaRecuperada.getComanda().getId());
                return contaRecuperada;
            } else {
                log.error("[CONCORRENCIA] Thread {} - DataIntegrityViolationException inesperada ao criar Conta {} para Comanda {}. Relançando. Causa: {}",
                        Thread.currentThread().getName(), numeroConta, comandaId, e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
                throw e;
            }
        }
    }

    private boolean isUniqueConstraintViolation(DataIntegrityViolationException e, String constraintName) {
        Throwable mostSpecificCause = e.getMostSpecificCause();

        if (mostSpecificCause instanceof SQLException) {
            SQLException sqlException = (SQLException) mostSpecificCause;
            if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlException.getSQLState())) {
                return sqlException.getMessage() != null && sqlException.getMessage().contains(constraintName);
            }
        }
        return false;
    }

    @Transactional
    public PedidoResponseDTO receberPagamento(UUID id, PagamentoRequestDTO dto) {
        Pedido pedido = pedidoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lote de pedidos não localizado para baixa."));

        synchronized (pedido.getId().toString().intern()) {
            if (pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO) {
                throw new BusinessRuleException("Operação negada: Este lote de pedidos já foi liquidado no caixa.");
            }

            pagamentoService.registrarPagamentoPedido(id, dto); // Passa o ID do pedido

            pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);
            pedido.setFormaPagamento(dto.formaPagamento());
            pedido.setValorRecebido(dto.valorRecebido());

            Pedido pedidoSalvo = pedidoRepository.save(pedido);
            PedidoResponseDTO response = new PedidoResponseDTO(pedidoSalvo);
            messagingTemplate.convertAndSend("/topic/caixa", response);
            return response;
        }
    }

    @Transactional
    public PedidoResponseDTO finalizarDelivery(CheckoutDeliveryRequestDTO dto) {
        validarCaixaAberto();

        Cliente cliente = clienteRepository.findById(dto.clienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não localizado para o pedido de Delivery."));

        Pedido pedido = new Pedido();
        pedido.setCliente(cliente);
        pedido.setTipo(TipoPedido.DELIVERY);
        pedido.setEnderecoEntrega(dto.enderecoEntrega());
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setFormaPagamento(null);
        pedido.setValorRecebido(null);
        pedido.setObservacaoGeral(dto.observacao());
        pedido.setItens(new ArrayList<>());

        List<CheckoutDeliveryItemCorrelation> explicitCorrelations = new ArrayList<>();
        List<ItemCarrinhoToItemPedidoCorrelation> cartCorrelations = new ArrayList<>();
        boolean necessitaPreparoCozinha = false;

        if (dto.itens() != null && !dto.itens().isEmpty()) {
            // Fluxo PDV Delivery com itens explícitos
            for (CheckoutDeliveryItemRequestDTO itemDto : dto.itens()) {
                Produto produto = produtoRepository.findById(itemDto.produtoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto do cardápio não localizado."));

                List<Adicional> adicionaisVinculados = itemDto.adicionaisIds() != null ?
                        adicionalRepository.findAllById(itemDto.adicionaisIds()) : new ArrayList<>();

                BigDecimal precoAdicionais = adicionaisVinculados.stream()
                        .map(Adicional::getPreco)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal precoFinalItemUnitario = produto.getPreco().add(precoAdicionais);
                if (precoFinalItemUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessRuleException("Preço final do item não pode ser zero ou negativo.");
                }

                ItemPedido item = new ItemPedido();
                item.setProduto(produto);
                item.setQuantidade(itemDto.quantidade());
                item.setPrecoUnitario(precoFinalItemUnitario);
                item.setAdicionais(adicionaisVinculados);
                item.setPedido(pedido);
                item.setObservacaoItem(itemDto.observacao());
                item.setStatusPagamento(StatusPagamento.ABERTO);

                pedido.getItens().add(item);
                if (produto.getPrecisaPreparo()) {
                    necessitaPreparoCozinha = true;
                }
                explicitCorrelations.add(new CheckoutDeliveryItemCorrelation(itemDto, item));
            }
        } else {
            // Fluxo legado Delivery pelo Carrinho backend
            Carrinho carrinho = localizarCarrinho(dto.clienteId());
            validarCarrinhoNaoVazio(carrinho);
            cartCorrelations = copiarItensDoCarrinho(carrinho, pedido); // Agora retorna as correlações
            // Determine if prep is needed for legacy flow
            necessitaPreparoCozinha = pedido.getItens().stream()
                    .anyMatch(item -> Boolean.TRUE.equals(item.getProduto().getPrecisaPreparo()));
        }

        calcularEPreencherTotal(pedido);
        Pedido pedidoSalvo = salvarPedido(pedido);

        Map<UUID, Map<UUID, ItemCombo>> comboSnapshotsCriados = criarSnapshotsDosCombos(pedidoSalvo);

        // Apply customizations for explicit items (PDV Delivery)
        for (CheckoutDeliveryItemCorrelation correlation : explicitCorrelations) {
            CheckoutDeliveryItemRequestDTO itemDto = correlation.payloadDTO();
            ItemPedido itemPedido = correlation.itemPedido();

            if (itemDto.itensCombo() != null && !itemDto.itensCombo().isEmpty()) {
                Map<UUID, ItemCombo> itemComboMap = comboSnapshotsCriados.get(itemPedido.getId());
                if (itemComboMap == null || itemComboMap.isEmpty()) {
                    throw new BusinessRuleException("Snapshots de combo não encontrados para o ItemPedido " + itemPedido.getId());
                }
                aplicarCustomizacoesDosItensCombo(itemPedido, itemComboMap, itemDto.itensCombo());
            }
        }

        // Apply customizations for cart items (Legacy Delivery)
        for (ItemCarrinhoToItemPedidoCorrelation correlation : cartCorrelations) {
            ItemCarrinho itemCarrinho = correlation.itemCarrinho();
            ItemPedido itemPedido = correlation.itemPedido();

            if (Boolean.TRUE.equals(itemCarrinho.getProduto().getIsCombo()) &&
                itemCarrinho.getCustomizacoesCombo() != null &&
                !itemCarrinho.getCustomizacoesCombo().isEmpty()) {

                Map<UUID, ItemCombo> itemComboMap = comboSnapshotsCriados.get(itemPedido.getId());
                if (itemComboMap == null || itemComboMap.isEmpty()) {
                    throw new BusinessRuleException("Snapshots de combo não encontrados para o ItemPedido " + itemPedido.getId());
                }

                // Converter ItemCarrinhoComboCustomizacao para ItemComboCustomizacaoRequestDTO
                List<ItemComboCustomizacaoRequestDTO> customizacoesParaAplicar = itemCarrinho.getCustomizacoesCombo().stream()
                        .map(cartCustom -> new ItemComboCustomizacaoRequestDTO(
                                cartCustom.getComboProdutoId(),
                                cartCustom.getAdicionais().stream().map(Adicional::getId).collect(Collectors.toList())
                        ))
                        .collect(Collectors.toList());

                aplicarCustomizacoesDosItensCombo(itemPedido, itemComboMap, customizacoesParaAplicar);
            }
        }


        // Recalculate total after applying combo customizations (if any)
        recalcularTotalPedido(pedidoSalvo.getId());

        if (necessitaPreparoCozinha) {
            gerarFilaImpressao(pedidoSalvo);
        }

        // Only clear cart if it was the legacy flow
        if (dto.itens() == null || dto.itens().isEmpty()) {
            limparCarrinho(localizarCarrinho(dto.clienteId()));
        }

        return new PedidoResponseDTO(pedidoSalvo);
    }

    @Transactional
    public PedidoResponseDTO finalizarRetirada(CheckoutRetiradaRequestDTO dto) {
        validarCaixaAberto();

        Carrinho carrinho = localizarCarrinho(dto.clienteId());
        validarCarrinhoNaoVazio(carrinho);

        Pedido pedido = new Pedido();
        pedido.setCliente(carrinho.getCliente());
        pedido.setTipo(TipoPedido.RETIRADA);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setFormaPagamento(null);
        pedido.setValorRecebido(null);
        pedido.setObservacaoGeral(dto.observacao());
        pedido.setItens(new ArrayList<>());

        List<ItemCarrinhoToItemPedidoCorrelation> cartCorrelations = copiarItensDoCarrinho(carrinho, pedido); // Retorna as correlações
        calcularEPreencherTotal(pedido);

        Pedido pedidoSalvo = salvarPedido(pedido);
        Map<UUID, Map<UUID, ItemCombo>> comboSnapshotsCriados = criarSnapshotsDosCombos(pedidoSalvo);

        // Apply customizations for cart items
        for (ItemCarrinhoToItemPedidoCorrelation correlation : cartCorrelations) {
            ItemCarrinho itemCarrinho = correlation.itemCarrinho();
            ItemPedido itemPedido = correlation.itemPedido();

            if (Boolean.TRUE.equals(itemCarrinho.getProduto().getIsCombo()) &&
                itemCarrinho.getCustomizacoesCombo() != null &&
                !itemCarrinho.getCustomizacoesCombo().isEmpty()) {

                Map<UUID, ItemCombo> itemComboMap = comboSnapshotsCriados.get(itemPedido.getId());
                if (itemComboMap == null || itemComboMap.isEmpty()) {
                    throw new BusinessRuleException("Snapshots de combo não encontrados para o ItemPedido " + itemPedido.getId());
                }

                List<ItemComboCustomizacaoRequestDTO> customizacoesParaAplicar = itemCarrinho.getCustomizacoesCombo().stream()
                        .map(cartCustom -> new ItemComboCustomizacaoRequestDTO(
                                cartCustom.getComboProdutoId(),
                                cartCustom.getAdicionais().stream().map(Adicional::getId).collect(Collectors.toList())
                        ))
                        .collect(Collectors.toList());

                aplicarCustomizacoesDosItensCombo(itemPedido, itemComboMap, customizacoesParaAplicar);
            }
        }
        recalcularTotalPedido(pedidoSalvo.getId()); // Recalcular após aplicar customizações

        gerarFilaImpressao(pedidoSalvo);
        limparCarrinho(carrinho);

        return new PedidoResponseDTO(pedidoSalvo);
    }

    @Transactional
    public PedidoResponseDTO finalizarMesa(CheckoutMesaRequestDTO dto) {
        validarCaixaAberto();

        if (dto.nomeResponsavel() == null || dto.nomeResponsavel().isBlank()) {
            throw new BusinessRuleException("Operação negada: O nome do responsável é obrigatório para o fechamento da MESA.");
        }

        Conta conta = contaRepository.findByComandaIdAndNumeroContaForUpdate(dto.comandaId(), dto.numeroConta())
                .orElseThrow(() -> new ResourceNotFoundException("Erro de domínio: Conta de atendimento da mesa não localizada."));

        Comanda comanda = conta.getComanda();

        Pedido pedidoReferencia = conta.getPedidos().stream()
                .filter(p -> p.getStatus() != StatusPedido.CANCELADO)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("A conta não possui pedidos válidos para serem finalizados."));

        conta.setNomeResponsavel(dto.nomeResponsavel().toUpperCase().trim());
        if (dto.telefoneResponsavel() != null && !dto.telefoneResponsavel().isBlank()) {
            conta.setTelefoneResponsavel(dto.telefoneResponsavel().trim());
        }

        BigDecimal totalConta = conta.getPedidos().stream()
                .filter(p -> p.getStatus() != StatusPedido.CANCELADO)
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        conta.setValorTotal(totalConta);
        contaRepository.saveAndFlush(conta);

        PagamentoRequestDTO pagamentoRequestDTO = new PagamentoRequestDTO(
                dto.formaPagamento(),
                dto.valorRecebido()
        );
        pagamentoService.registrarPagamento(conta.getId(), pagamentoRequestDTO);

        conta = contaRepository.findById(conta.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada após registro de pagamento."));

        if (Boolean.TRUE.equals(conta.getPago())) {
            for (Pedido p : conta.getPedidos()) {
                if (p.getStatus() != StatusPedido.CANCELADO) {
                    p.setStatus(StatusPedido.FINALIZADO);
                    pedidoRepository.save(p);
                }
            }

            if (comanda != null) {
                List<Conta> todasContas = contaRepository.findByComandaId(comanda.getId());
                boolean todasPagas = todasContas.stream()
                        .allMatch(c -> Boolean.TRUE.equals(c.getPago()));
                if (todasPagas) {
                    comanda.setStatus(StatusComanda.FECHADA);
                    comanda.setFechadaEm(LocalDateTime.now());
                    comandaRepository.save(comanda);
                }
            }
        } else {
            log.info("Pagamento parcial para Conta {}. Pedidos e Comanda permanecem abertos operacionalmente.", conta.getId());
        }

        return new PedidoResponseDTO(pedidoReferencia);
    }

    @Transactional
    public PedidoResponseDTO finalizarBalcao(CheckoutBalcaoRequestDTO dto) {
        validarCaixaAberto();

        Pedido pedido = new Pedido();
        pedido.setTipo(TipoPedido.BALCAO);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setStatusFinanceiro(StatusFinanceiro.AGUARDANDO_PAGAMENTO);
        pedido.setObservacaoGeral(dto.observacao());
        pedido.setNomeClienteBalcao(dto.nomeConsumidor() != null ? dto.nomeConsumidor().toUpperCase().trim() : "CONSUMIDOR PADRÃO");
        pedido.setItens(new ArrayList<>());

        copiarItensDasRequests(dto.itens(), pedido);
        calcularEPreencherTotal(pedido);

        Pedido pedidoSalvo = salvarPedido(pedido);

        PagamentoRequestDTO pagamentoDto = new PagamentoRequestDTO(
                dto.formaPagamento(),
                dto.valorRecebido()
        );
        pagamentoService.registrarPagamentoPedido(
                pedidoSalvo.getId(), // Passa o ID do pedido
                pagamentoDto
        );

        pedidoSalvo.setStatusFinanceiro(StatusFinanceiro.PAGO);
        pedidoSalvo.setFormaPagamento(dto.formaPagamento());
        pedidoSalvo.setValorRecebido(dto.valorRecebido());
        pedidoRepository.save(pedidoSalvo);

        criarSnapshotsDosCombos(pedidoSalvo);
        gerarFilaImpressao(pedidoSalvo);

        return new PedidoResponseDTO(pedidoSalvo);
    }

    private void validarCaixaAberto() {
        if (!caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Operação negada: O turno do caixa está fechado.");
        }
    }

    private Carrinho localizarCarrinho(UUID clienteId) {
        return carrinhoRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho de compras não localizado para este cliente."));
    }

    private void validarCarrinhoNaoVazio(Carrinho carrinho) {
        if (carrinho.getItens().isEmpty()) {
            throw new BusinessRuleException("Operação negada: Não é possível finalizar um checkout com o carrinho vazio.");
        }
    }

    private List<ItemCarrinhoToItemPedidoCorrelation> copiarItensDoCarrinho(Carrinho carrinho, Pedido pedido) {
        List<ItemCarrinhoToItemPedidoCorrelation> correlations = new ArrayList<>();
        for (ItemCarrinho itemCarrinho : carrinho.getItens()) {
            ItemPedido item = new ItemPedido();
            item.setProduto(itemCarrinho.getProduto());
            item.setQuantidade(itemCarrinho.getQuantidade());

            BigDecimal precoAdicionais = BigDecimal.ZERO;
            if (itemCarrinho.getAdicionais() != null && !itemCarrinho.getAdicionais().isEmpty()) {
                precoAdicionais = itemCarrinho.getAdicionais().stream()
                        .map(Adicional::getPreco)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                item.setAdicionais(new ArrayList<>(itemCarrinho.getAdicionais()));
            }

            item.setPrecoUnitario(itemCarrinho.getProduto().getPreco().add(precoAdicionais));
            item.setObservacaoItem(itemCarrinho.getObservacao());

            item.setPedido(pedido);
            item.setStatusPagamento(StatusPagamento.ABERTO);
            pedido.getItens().add(item);
            correlations.add(new ItemCarrinhoToItemPedidoCorrelation(itemCarrinho, item));
        }
        return correlations;
    }

    private void copiarItensDasRequests(List<ItemPedidoRequestDTO> itens, Pedido pedido) {
        if (itens != null) {
            for (ItemPedidoRequestDTO itemDto : itens) {
                Produto produto = produtoRepository.findById(itemDto.produtoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto do cardápio não localizado."));

                List<Adicional> adicionaisVinculados = itemDto.adicionaisIds() != null ?
                        adicionalRepository.findAllById(itemDto.adicionaisIds()) : new ArrayList<>();

                BigDecimal precoAdicionais = adicionaisVinculados.stream()
                        .map(Adicional::getPreco)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal precoFinalItemUnitario = produto.getPreco().add(precoAdicionais);
                if (precoFinalItemUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessRuleException("Preço final do item não pode ser zero ou negativo.");
                }

                ItemPedido item = new ItemPedido();
                item.setProduto(produto);
                item.setQuantidade(itemDto.quantidade());
                item.setPrecoUnitario(precoFinalItemUnitario);
                item.setAdicionais(adicionaisVinculados);
                item.setPedido(pedido);
                item.setStatusPagamento(StatusPagamento.ABERTO);
                pedido.getItens().add(item);
            }
        }
    }

    private void calcularEPreencherTotal(Pedido pedido) {
        BigDecimal somaTotal = BigDecimal.ZERO;
        for (ItemPedido item : pedido.getItens()) {
            somaTotal = somaTotal.add(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
        }
        pedido.setTotal(somaTotal);
    }

    private Pedido salvarPedido(Pedido pedido) {
        return pedidoRepository.save(pedido);
    }

    private void gerarFilaImpressao(Pedido pedido) {
        FilaImpressao cupomCozinha = new FilaImpressao();
        cupomCozinha.setPedido(pedido);
        cupomCozinha.setDestino(FilaImpressao.DestinoImpressao.COZINHA);
        cupomCozinha.setStatus(FilaImpressao.StatusImpressao.PENDENTE);
        filaImpressaoRepository.save(cupomCozinha);
    }

    private void limparCarrinho(Carrinho carrinho) {
        carrinho.getItens().clear();
        carrinhoRepository.save(carrinho);
    }

    @Transactional
    public PedidoResponseDTO adicionarItemPedido(UUID pedidoId, ItemPedidoRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido informado não localizado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO) {
            throw new BusinessRuleException("Bloqueio operacional: Não é permitido alterar pedidos em subcontas liquidadas.");
        }

        Produto produto = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não localizado no catálogo."));

        ItemPedido novoItem = new ItemPedido();
        novoItem.setProduto(produto);
        novoItem.setQuantidade(dto.quantidade());
        novoItem.setPrecoUnitario(produto.getPreco());
        novoItem.setPedido(pedido);
        novoItem.setNumeroConta(dto.numeroConta());
        novoItem.setStatusPagamento(StatusPagamento.ABERTO);

        pedido.getItens().add(novoItem);
        pedido.setTotal(pedido.getTotal().add(produto.getPreco().multiply(BigDecimal.valueOf(dto.quantidade()))));

        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        criarSnapshotsDosCombos(pedidoSalvo);
        PedidoResponseDTO response = new PedidoResponseDTO(pedidoSalvo);
        messagingTemplate.convertAndSend("/topic/caixa", response);
        return response;
    }

    @Transactional
    public PedidoResponseDTO removerItemPedido(UUID pedidoId, UUID itemId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido informado não localizado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO) {
            throw new BusinessRuleException("Bloqueio operacional: Pedido encerrado não pode sofrer alterações.");
        }

        ItemPedido itemRemover = pedido.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item não localizado no lote de pedidos."));

        BigDecimal valorDeduzir = itemRemover.getPrecoUnitario().multiply(BigDecimal.valueOf(itemRemover.getQuantidade()));
        pedido.setTotal(pedido.getTotal().subtract(valorDeduzir));

        List<ItemCombo> itensCombo =
                itemComboRepository.findByItemPedidoId(
                        itemRemover.getId()
                );

        if (!itensCombo.isEmpty()) {
            itemComboRepository.deleteAll(itensCombo);
            itemComboRepository.flush();
        }

        pedido.getItens().remove(itemRemover);

        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        PedidoResponseDTO response = new PedidoResponseDTO(pedidoSalvo);
        messagingTemplate.convertAndSend("/topic/caixa", response);
        return response;
    }

    @Transactional
    public PedidoResponseDTO atualizarAdicionaisDoItem(UUID pedidoId, UUID itemId, List<UUID> adicionaisIds) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido informado não localizado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO) {
            throw new BusinessRuleException("Bloqueio operacional: Pedido inalterável.");
        }

        ItemPedido item = pedido.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item de pedido não localizado."));

        List<Adicional> adicionais = adicionalRepository.findAllById(adicionaisIds);
        item.setAdicionais(adicionais);

        return recalcularTotalPedido(pedido.getId());
    }

    @Transactional
    public PedidoResponseDTO atualizarStatus(UUID id, PedidoStatusRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não localizado."));

        pedido.setStatus(dto.status());
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        PedidoResponseDTO response = new PedidoResponseDTO(pedidoSalvo);
        messagingTemplate.convertAndSend("/topic/caixa", response);
        messagingTemplate.convertAndSend("/topic/cozinha", response);
        return response;
    }

    @Transactional
    public PedidoResponseDTO cancelarPedido(UUID id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não localizado."));

        BigDecimal saldoLiquido = pagamentoService.getSaldoLiquidoPagoPorPedido(id);

        if (saldoLiquido.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException(
                    "Operação negada: O pedido possui pagamento ativo. Realize o estorno financeiro antes do cancelamento."
            );
        }

        pedido.setStatus(StatusPedido.CANCELADO);

        if (pedido.getStatusFinanceiro() == StatusFinanceiro.AGUARDANDO_PAGAMENTO) {
            pedido.setStatusFinanceiro(StatusFinanceiro.CANCELADO);
        } else if (pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO) {
            pedido.setStatusFinanceiro(StatusFinanceiro.ESTORNADO);
        } else if (pedido.getStatusFinanceiro() == StatusFinanceiro.ESTORNADO) { // Explicitly keep ESTORNADO
            pedido.setStatusFinanceiro(StatusFinanceiro.ESTORNADO);
        }
        // If it was already ESTORNADO, it remains ESTORNADO. No explicit action needed.

        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        PedidoResponseDTO response = new PedidoResponseDTO(pedidoSalvo);

        messagingTemplate.convertAndSend("/topic/caixa", response);
        messagingTemplate.convertAndSend("/topic/cozinha", response);

        return response;
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPedidosAtivosMonitor() {
        return pedidoRepository.findAll().stream()
                .filter(p -> p.getStatus() != StatusPedido.FINALIZADO && p.getStatus() != StatusPedido.CANCELADO)
                .map(PedidoResponseDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarHistoricoCliente(UUID clienteId) {
        return pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId).stream()
                .map(PedidoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        return pedidoRepository.findAllWithMesaDetails().stream().map(PedidoResponseDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(UUID id) {
        return new PedidoResponseDTO(pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não localizado.")));
    }

    @Transactional(readOnly = true)
    public List<ItemComandaMobileResponseDTO> buscarItensPorComandaMestre(UUID comandaId) {
        List<Conta> contasAssociadas = contaRepository.findByComandaId(comandaId);
        List<UUID> contaIds = contasAssociadas.stream().map(Conta::getId).toList();

        List<Pedido> pedidosDaMesa = pedidoRepository.findByContaIdIn(contaIds);
        List<ItemComandaMobileResponseDTO> listagemFinal = new ArrayList<>();

        for (Conta c : contasAssociadas) {
            List<Pedido> pedidosDaSubconta = pedidosDaMesa.stream()
                    .filter(p -> p.getConta() != null && p.getConta().getId().equals(c.getId()))
                    .toList();

            for (Pedido p : pedidosDaSubconta) {
                String nomeCli = c.getNomeResponsavel();
                if (nomeCli == null || nomeCli.isBlank()) {
                    nomeCli = p.getNomeClienteBalcao();
                }

                ItemComandaMobileResponseDTO.ClienteMesaDTO cliMesa = (nomeCli != null && !nomeCli.isBlank())
                        ? new ItemComandaMobileResponseDTO.ClienteMesaDTO(nomeCli, null)
                        : null;

                for (ItemPedido item : p.getItens()) {
                    BigDecimal precoCalculado = item.getPrecoUnitario();
                    BigDecimal precoTotalCalculadoItem = precoCalculado.multiply(BigDecimal.valueOf(item.getQuantidade()));

                    listagemFinal.add(new ItemComandaMobileResponseDTO(
                            item.getProduto().getId(),
                            item.getProduto().getNome(),
                            item.getQuantidade(),
                            precoTotalCalculadoItem,
                            item.getObservacaoItem(),
                            c.getNumeroConta(),
                            item.getAdicionais(),
                            cliMesa,
                            comandaId
                    ));
                }
            }
        }
        return listagemFinal;
    }

    private void vincularResponsavelMesa(Pedido pedido, Conta conta) {
        String clienteNome;
        if (conta.hasRealResponsavel()) {
            clienteNome = conta.getNomeResponsavel().trim().toUpperCase();
        } else {
            clienteNome = String.format(
                    "MESA %d - CONTA %d",
                    conta.getComanda().getMesa().getNumero(),
                    conta.getNumeroConta()
            );
        }
        pedido.setNomeClienteBalcao(clienteNome);
        log.info("[PEDIDO-MESA] Mesa: {}, Conta: {}, Cliente propagado: {}",
                conta.getComanda().getMesa().getNumero(),
                conta.getNumeroConta(),
                clienteNome);
    }

    @Transactional
    public PedidoResponseDTO recalcularTotalPedido(UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido informado não localizado."));

        BigDecimal totalItensPedido = pedido.getItens().stream()
                .map(item -> {
                    BigDecimal precoBaseItem = item.getPrecoUnitario();
                    return precoBaseItem.multiply(BigDecimal.valueOf(item.getQuantidade()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAdicionaisItemCombo = calcularTotalAdicionaisItensCombo(pedido);

        BigDecimal novoTotalPedido = totalItensPedido.add(totalAdicionaisItemCombo);

        pedido.setTotal(novoTotalPedido);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        PedidoResponseDTO response = new PedidoResponseDTO(pedidoSalvo);
        messagingTemplate.convertAndSend("/topic/caixa", response);
        return response;
    }

    private BigDecimal calcularTotalAdicionaisItensCombo(Pedido pedido) {
        BigDecimal totalAdicionaisCombos = BigDecimal.ZERO;

        for (ItemPedido itemPedido : pedido.getItens()) {
            if (itemPedido.getProduto() != null && itemPedido.getProduto().getIsCombo()) {
                List<ItemCombo> itensCombo = itemComboRepository.findByItemPedidoId(itemPedido.getId());

                for (ItemCombo itemCombo : itensCombo) {
                    BigDecimal precoAdicionaisItemCombo = itemCombo.getAdicionais().stream()
                            .map(Adicional::getPreco)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    totalAdicionaisCombos = totalAdicionaisCombos.add(
                            precoAdicionaisItemCombo.multiply(BigDecimal.valueOf(itemCombo.getQuantidade()))
                    );
                }
            }
        }
        return totalAdicionaisCombos;
    }

    private Map<UUID, ItemCombo> criarSnapshotsDoCombo(ItemPedido itemPedido) {
        if (itemPedido == null
                || itemPedido.getProduto() == null
                || itemPedido.getId() == null) {
            return new HashMap<>();
        }

        if (!Boolean.TRUE.equals(itemPedido.getProduto().getIsCombo())) {
            return new HashMap<>();
        }

        List<ItemCombo> existentes = itemComboRepository.findByItemPedidoId(itemPedido.getId());
        if (!existentes.isEmpty()) {
            // If snapshots already exist, we assume they were created in a previous call
            // and we don't need to re-create or correlate them for *this* creation flow.
            // The instruction states "A correlação é necessária apenas durante a criação."
            return new HashMap<>();
        }

        List<ComboProduto> composicao = comboProdutoRepository.findByComboId(itemPedido.getProduto().getId());

        if (composicao.isEmpty()) {
            return new HashMap<>();
        }

        List<ItemCombo> snapshots = new ArrayList<>();
        Map<UUID, ItemCombo> comboProdutoIdToItemComboMap = new HashMap<>();

        for (ComboProduto config : composicao) {
            Produto produtoInterno = config.getProduto();
            ItemCombo itemCombo = new ItemCombo();

            itemCombo.setItemPedido(itemPedido);
            itemCombo.setProdutoId(produtoInterno.getId());
            itemCombo.setNomeProduto(produtoInterno.getNome());
            itemCombo.setQuantidade(config.getQuantidade());
            itemCombo.setPrecoUnitario(produtoInterno.getPreco());
            itemCombo.setAdicionais(new ArrayList<>()); // Initialize empty list for new ItemCombo

            snapshots.add(itemCombo);
            comboProdutoIdToItemComboMap.put(config.getId(), itemCombo); // Correlate ComboProduto.id with ItemCombo
        }

        itemComboRepository.saveAll(snapshots);
        return comboProdutoIdToItemComboMap;
    }

    private Map<UUID, Map<UUID, ItemCombo>> criarSnapshotsDosCombos(Pedido pedido) {
        if (pedido == null || pedido.getItens() == null) {
            return new HashMap<>();
        }

        Map<UUID, Map<UUID, ItemCombo>> allComboSnapshots = new HashMap<>();
        for (ItemPedido itemPedido : pedido.getItens()) {
            if (Boolean.TRUE.equals(itemPedido.getProduto().getIsCombo())) {
                Map<UUID, ItemCombo> comboSnapshots = criarSnapshotsDoCombo(itemPedido);
                if (!comboSnapshots.isEmpty()) {
                    allComboSnapshots.put(itemPedido.getId(), comboSnapshots);
                }
            }
        }
        return allComboSnapshots;
    }

    private void aplicarCustomizacoesDosItensCombo(
            ItemPedido itemPedidoPai,
            Map<UUID, ItemCombo> comboProdutoIdToItemComboMap,
            List<ItemComboCustomizacaoRequestDTO> customizacoes
    ) {
        Produto comboProdutoPrincipal = itemPedidoPai.getProduto();

        for (ItemComboCustomizacaoRequestDTO customizacao : customizacoes) {
            UUID comboProdutoId = customizacao.comboProdutoId();
            List<UUID> adicionaisIds = customizacao.adicionaisIds();

            // 1. Validar que comboProdutoId pertence ao Combo do ItemPedido
            ComboProduto comboProdutoConfig = comboProdutoRepository.findById(comboProdutoId)
                    .orElseThrow(() -> new BusinessRuleException("Configuração de ComboProduto não localizada para o ID: " + comboProdutoId));

            if (!comboProdutoConfig.getCombo().getId().equals(comboProdutoPrincipal.getId())) {
                throw new BusinessRuleException("ComboProduto com ID " + comboProdutoId + " não pertence ao combo principal " + comboProdutoPrincipal.getNome());
            }

            // 2. O ItemCombo correspondente foi realmente criado
            ItemCombo itemCombo = comboProdutoIdToItemComboMap.get(comboProdutoId);
            if (itemCombo == null) {
                throw new BusinessRuleException("ItemCombo correspondente ao ComboProduto " + comboProdutoId + " não foi encontrado após a criação dos snapshots.");
            }

            // 3. Validar e aplicar os adicionais
            if (adicionaisIds != null && !adicionaisIds.isEmpty()) {
                List<Adicional> adicionaisValidados = adicionalValidationService.validarAdicionaisPermitidos(itemCombo.getProdutoId(), adicionaisIds);
                itemCombo.setAdicionais(adicionaisValidados);
            } else {
                itemCombo.setAdicionais(new ArrayList<>()); // Clear if empty list is sent
            }
            itemComboRepository.save(itemCombo); // Save the updated ItemCombo
        }
    }
}