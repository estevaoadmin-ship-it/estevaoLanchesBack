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
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors; // Adicionado para Collectors.toList()

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);
    private static final String UNIQUE_CONSTRAINT_CONTA_NUMERO = "uk_comanda_id_numero_conta"; // Nome da constraint
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505"; // SQLState padrão para violação de unicidade (PostgreSQL)

    private final PedidoRepository pedidoRepository;
    private final CarrinhoRepository carrinhoRepository;
    private final CaixaRepository caixaRepository;
    private final ProdutoRepository produtoRepository;
    private final AdicionalRepository adicionalRepository;
    private final FilaImpressaoRepository filaImpressaoRepository;
    private final ComandaRepository comandaRepository;
    private final ContaRepository contaRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ItemComboRepository itemComboRepository; // Adicionado para a nova feature
    private final ComboProdutoRepository comboProdutoRepository; // ADICIONADO
    // private final ItemComboService itemComboService; // INJETADO: ItemComboService - REMOVIDO

    /**
     * 📱 FLUXO MOBILE: Processa a entrada de lotes enviados por garçons ou tablets.
     */
    @Transactional
    public PedidoResponseDTO processarPedidoMobile(PedidoMobileRequestDTO dto) {
        if (!caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Operação negada: O turno do caixa está fechado.");
        }

        // CORREÇÃO OBRIGATÓRIA: Restaurar o comportamento antigo de buscar a Conta primeiro
        // A blindagem (getOrCreateAccountWithConcurrencyProtection) será chamada SOMENTE se a Conta não existir.
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
        }

        pedido.setTotal(subtotalLote);
        Pedido pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        // CHAMADA NOVA: Gerar snapshots de ItemCombo após a persistência do pedido
        // itemComboService.criarSnapshotsDosCombos(pedidoSalvo); // REMOVIDO
        criarSnapshotsDosCombos(pedidoSalvo); // SUBSTITUIDO

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

    /**
     * Método privado que encapsula toda a lógica de localizar, criar, tratar concorrência,
     * recuperar após conflito e validar uma Conta.
     *
     * @param comandaId O ID da Comanda à qual a Conta pertence.
     * @param numeroConta O número da Conta a ser localizada ou criada.
     * @return A Conta localizada ou criada, garantindo unicidade.
     * @throws BusinessRuleException Se houver inconsistência na recuperação da Conta após conflito.
     * @throws ResourceNotFoundException Se a Comanda mestre não for encontrada.
     */
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
            // AJUSTE FINAL: Utilizar saveAndFlush() para detecção imediata da violação de unicidade
            Conta contaSalva = contaRepository.saveAndFlush(novaConta);
            return contaSalva;
        } catch (DataIntegrityViolationException e) {
            // AJUSTE 1: Verificar se a exceção é causada pela constraint uk_comanda_id_numero_conta
            if (isUniqueConstraintViolation(e, UNIQUE_CONSTRAINT_CONTA_NUMERO)) {
                log.warn("[CONCORRENCIA] Thread {} - Conflito detectado ({}). Re-buscando Conta existente. Comanda ID: {}, Numero Conta: {}. Erro: {}",
                        Thread.currentThread().getName(), UNIQUE_CONSTRAINT_CONTA_NUMERO, comandaId, numeroConta, e.getMessage());

                // AJUSTE 2: Validar a conta recuperada
                Conta contaRecuperada = contaRepository.findByComandaIdAndNumeroConta(comandaId, numeroConta)
                        .orElseThrow(() -> {
                            log.error("[CONCORRENCIA] Thread {} - Erro grave: Conta não encontrada após conflito de concorrência esperado. Comanda ID: {}, Numero Conta: {}", Thread.currentThread().getName(), comandaId, numeroConta);
                            return new BusinessRuleException("Erro de concorrência: Conta deveria existir, mas não foi encontrada após conflito. Comanda ID: " + comandaId + ", Numero Conta: " + numeroConta);
                        });

                // Further validation for AJUSTE 2
                if (!contaRecuperada.getNumeroConta().equals(numeroConta) || !contaRecuperada.getComanda().getId().equals(comandaId)) {
                    log.error("[CONCORRENCIA] Thread {} - Erro grave: Conta recuperada após conflito não corresponde aos dados esperados. Esperado Comanda ID: {}, Numero Conta: {}. Encontrado Comanda ID: {}, Numero Conta: {}",
                            Thread.currentThread().getName(), comandaId, numeroConta, contaRecuperada.getComanda().getId(), contaRecuperada.getNumeroConta());
                    throw new BusinessRuleException("Erro de concorrência: Conta recuperada após conflito não corresponde aos dados esperados. Comanda ID: " + comandaId + ", Numero Conta: " + numeroConta);
                }
                log.info("[CONCORRENCIA] Thread {} - Conta {} para Comanda {} recuperada com sucesso após conflito.", Thread.currentThread().getName(), contaRecuperada.getNumeroConta(), contaRecuperada.getComanda().getId());
                return contaRecuperada;
            } else {
                // Se não for a constraint esperada, relançar a exceção original
                log.error("[CONCORRENCIA] Thread {} - DataIntegrityViolationException inesperada ao criar Conta {} para Comanda {}. Relançando. Causa: {}",
                        Thread.currentThread().getName(), numeroConta, comandaId, e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Documentação da Limitação:
     * Este método verifica se uma DataIntegrityViolationException é causada por uma violação de unicidade
     * específica (uk_comanda_id_numero_conta). Ele depende do SQLState padrão '23505' para violações de unicidade
     * (comum em PostgreSQL) e da presença do nome da constraint na mensagem de erro da SQLException.
     *
     * Limitação: A análise da mensagem de erro (sqlException.getMessage().contains(constraintName)) é frágil
     * e pode ser afetada por mudanças na versão do banco de dados, driver JDBC ou configurações de idioma.
     *
     * Justificativa: Dentro das restrições do projeto (não adicionar bibliotecas, não alterar arquitetura
     * para mecanismos de tradução de exceções mais sofisticadas, não usar @Lock, @Version, etc.), esta é a
     * abordagem mais robusta e específica para identificar a violação da *nossa* constraint de unicidade.
     * O SQLState '23505' já garante que é uma violação de unicidade, e a verificação da mensagem refina para
     * a constraint específica, evitando mascarar outras possíveis violações de integridade.
     */
    private boolean isUniqueConstraintViolation(DataIntegrityViolationException e, String constraintName) {
        Throwable mostSpecificCause = e.getMostSpecificCause();

        if (mostSpecificCause instanceof SQLException) {
            SQLException sqlException = (SQLException) mostSpecificCause;
            if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlException.getSQLState())) {
                // A string do nome da constraint deve aparecer na mensagem de erro para identificação específica.
                // Esta é a parte mais frágil, mas necessária para diferenciar entre múltiplas UNIQUE constraints.
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
            if (pedido.getStatusFinanceiro() == StatusFinanceiro.PAGO || pedido.getStatus() == StatusPedido.FINALIZADO) {
                throw new BusinessRuleException("Operação negada: Este lote de pedidos já foi liquidado no caixa.");
            }

            pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);
            pedido.setFormaPagamento(dto.formaPagamento());
            pedido.setValorRecebido(dto.valorRecebido());
            pedido.setStatus(StatusPedido.FINALIZADO);

            if (pedido.getConta() != null) {
                pedido.getConta().setPago(true);
            }

            Pedido pedidoSalvo = pedidoRepository.save(pedido);
            PedidoResponseDTO response = new PedidoResponseDTO(pedidoSalvo);
            messagingTemplate.convertAndSend("/topic/caixa", response);
            return response;
        }
    }

    // ==========================================
    // 🛠️ FLUXOS DE ATENDIMENTO E CHECKOUTS
    // ==========================================

    /**
     * 🛵 FLUXO DELIVERY
     */
    @Transactional
    public PedidoResponseDTO finalizarDelivery(CheckoutDeliveryRequestDTO dto) {
        validarCaixaAberto();

        Carrinho carrinho = localizarCarrinho(dto.clienteId());
        validarCarrinhoNaoVazio(carrinho);

        Pedido pedido = new Pedido();
        pedido.setCliente(carrinho.getCliente());
        pedido.setTipo(TipoPedido.DELIVERY);
        pedido.setEnderecoEntrega(dto.enderecoEntrega());
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);
        pedido.setFormaPagamento(dto.formaPagamento());
        pedido.setObservacaoGeral(dto.observacao());
        pedido.setItens(new ArrayList<>());

        copiarItensDoCarrinho(carrinho, pedido);
        calcularEPreencherTotal(pedido);

        Pedido pedidoSalvo = salvarPedido(pedido);
        // CHAMADA NOVA: Gerar snapshots de ItemCombo após a persistência do pedido
        // itemComboService.criarSnapshotsDosCombos(pedidoSalvo); // REMOVIDO
        criarSnapshotsDosCombos(pedidoSalvo); // SUBSTITUIDO
        gerarFilaImpressao(pedidoSalvo);
        limparCarrinho(carrinho);

        return new PedidoResponseDTO(pedidoSalvo);
    }

    /**
     * 📋 FLUXO RETIRADA
     */
    @Transactional
    public PedidoResponseDTO finalizarRetirada(CheckoutRetiradaRequestDTO dto) {
        validarCaixaAberto();

        Carrinho carrinho = localizarCarrinho(dto.clienteId());
        validarCarrinhoNaoVazio(carrinho);

        Pedido pedido = new Pedido();
        pedido.setCliente(carrinho.getCliente());
        pedido.setTipo(TipoPedido.RETIRADA);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);
        pedido.setFormaPagamento(dto.formaPagamento());
        pedido.setObservacaoGeral(dto.observacao());
        pedido.setItens(new ArrayList<>());

        copiarItensDoCarrinho(carrinho, pedido);
        calcularEPreencherTotal(pedido);

        Pedido pedidoSalvo = salvarPedido(pedido);
        // CHAMADA NOVA: Gerar snapshots de ItemCombo após a persistência do pedido
        // itemComboService.criarSnapshotsDosCombos(pedidoSalvo); // REMOVIDO
        criarSnapshotsDosCombos(pedidoSalvo); // SUBSTITUIDO
        gerarFilaImpressao(pedidoSalvo);
        limparCarrinho(carrinho);

        return new PedidoResponseDTO(pedidoSalvo);
    }

    /**
     * 🪑 FLUXO MESA: Orquestra o fechamento da Conta e Comanda nativa.
     */
    @Transactional
    public PedidoResponseDTO finalizarMesa(CheckoutMesaRequestDTO dto) {
        validarCaixaAberto();

        if (dto.nomeResponsavel() == null || dto.nomeResponsavel().isBlank()) {
            throw new BusinessRuleException("Operação negada: O nome do responsável é obrigatório para o fechamento da MESA.");
        }

        Conta conta = contaRepository.findByComandaIdAndNumeroConta(dto.comandaId(), dto.numeroConta())
                .orElseThrow(() -> new ResourceNotFoundException("Erro de domínio: Conta de atendimento da mesa não localizada."));

        Comanda comanda = conta.getComanda();

        // 🎯 CORREÇÃO 1: Validação imediata do estado da Conta. Lança exceção se não houver pedidos reais.
        Pedido pedidoReferencia = (conta.getPedidos() != null && !conta.getPedidos().isEmpty())
                ? conta.getPedidos().get(0)
                : null;

        if (pedidoReferencia == null) {
            throw new BusinessRuleException("A conta não possui pedidos para serem finalizados.");
        }

        // Atualiza dados do responsável diretamente na Conta
        conta.setNomeResponsavel(dto.nomeResponsavel().toUpperCase().trim());
        if (dto.telefoneResponsavel() != null && !dto.telefoneResponsavel().isBlank()) {
            conta.setTelefoneResponsavel(dto.telefoneResponsavel().trim());
        }

        // Calcular os totais finais da conta baseado nos pedidos já criados pelo fluxo mobile
        BigDecimal totalConta = conta.getPedidos().stream()
                .filter(p -> p.getStatus() != StatusPedido.CANCELADO)
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        conta.setValorTotal(totalConta);
        conta.setPago(true);
        contaRepository.save(conta);

        // Registrar o pagamento e liquidar os lotes de pedidos existentes da conta
        for (Pedido p : conta.getPedidos()) {
            if (p.getStatus() != StatusPedido.CANCELADO) {
                p.setStatusFinanceiro(StatusFinanceiro.PAGO);
                p.setFormaPagamento(dto.formaPagamento());
                p.setValorRecebido(p.getTotal());
                p.setStatus(StatusPedido.FINALIZADO);
                pedidoRepository.save(p);
            }
        }

        // Finalizar a comanda mãe se todas as subcontas ativas estiverem encerradas
        if (comanda != null) {
            List<Conta> todasContas = contaRepository.findByComandaId(comanda.getId());
            boolean todasPagas = todasContas.stream()
                    .allMatch(c -> c.getId().equals(conta.getId()) || c.getPago());
            if (todasPagas) {
                // 🎯 CORREÇÃO 2: Chamada do método de encapsulamento rico da entidade Comanda.
                comanda.setStatus(StatusComanda.FECHADA);
                comanda.setFechadaEm(LocalDateTime.now());
                comandaRepository.save(comanda);

            }
        }

        return new PedidoResponseDTO(pedidoReferencia);
    }

    /**
     * 🛒 FLUXO BALCÃO
     */
    @Transactional
    public PedidoResponseDTO finalizarBalcao(CheckoutBalcaoRequestDTO dto) {
        validarCaixaAberto();

        Pedido pedido = new Pedido();
        pedido.setTipo(TipoPedido.BALCAO);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);
        pedido.setFormaPagamento(dto.formaPagamento());
        pedido.setObservacaoGeral(dto.observacao());
        pedido.setNomeClienteBalcao(dto.nomeConsumidor() != null ? dto.nomeConsumidor().toUpperCase().trim() : "CONSUMIDOR PADRÃO");
        pedido.setItens(new ArrayList<>());

        copiarItensDasRequests(dto.itens(), pedido);
        calcularEPreencherTotal(pedido);

        Pedido pedidoSalvo = salvarPedido(pedido);
        // CHAMADA NOVA: Gerar snapshots de ItemCombo após a persistência do pedido
        // itemComboService.criarSnapshotsDosCombos(pedidoSalvo); // REMOVIDO
        criarSnapshotsDosCombos(pedidoSalvo); // SUBSTITUIDO
        gerarFilaImpressao(pedidoSalvo);

        return new PedidoResponseDTO(pedidoSalvo);
    }

    // ==========================================
    // ⚙️ ROTINAS UTILITÁRIAS COMPARTILHADAS (GENÉRICAS)
    // ==========================================

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

    private void copiarItensDoCarrinho(Carrinho carrinho, Pedido pedido) {
        for (ItemCarrinho itemCarrinho : carrinho.getItens()) {
            ItemPedido item = new ItemPedido();
            item.setProduto(itemCarrinho.getProduto());
            item.setQuantidade(itemCarrinho.getQuantidade());
            item.setPrecoUnitario(itemCarrinho.getProduto().getPreco());
            item.setPedido(pedido);
            item.setStatusPagamento(StatusPagamento.PAGO);
            pedido.getItens().add(item);
        }
    }

    private void copiarItensDasRequests(List<ItemPedidoRequestDTO> itens, Pedido pedido) {
        if (itens != null) {
            for (ItemPedidoRequestDTO itemDto : itens) {
                Produto produto = produtoRepository.findById(itemDto.produtoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto do cardápio não localizado."));

                ItemPedido item = new ItemPedido();
                item.setProduto(produto);
                item.setQuantidade(itemDto.quantidade());
                item.setPrecoUnitario(produto.getPreco());
                item.setPedido(pedido);
                item.setStatusPagamento(StatusPagamento.PAGO);
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
        pedido.setValorRecebido(somaTotal);
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

    // ==========================================

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
        // CHAMADA NOVA: Gerar snapshots de ItemCombo após a persistência do pedido
        // itemComboService.criarSnapshotsDosCombos(pedidoSalvo); // REMOVIDO
        criarSnapshotsDosCombos(pedidoSalvo); // SUBSTITUIDO
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

        // ==================================================
        // ÚNICO BLOCO NOVO AUTORIZADO
        // ==================================================

        List<ItemCombo> itensCombo =
                itemComboRepository.findByItemPedidoId(
                        itemRemover.getId()
                );

        if (!itensCombo.isEmpty()) {
            itemComboRepository.deleteAll(itensCombo);
            itemComboRepository.flush();
        }

        // ==================================================
        // DAQUI PARA BAIXO, MANTER O FLUXO ANTIGO INTACTO
        // ==================================================
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

        // Recalcula o total do pedido, incluindo os adicionais de ItemPedido e ItemCombo
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

        pedido.setStatus(StatusPedido.CANCELADO);

        pedido.setStatusFinanceiro(StatusFinanceiro.CANCELADO);

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
    public List<PedidoResponseDTO> listarTodos() {
        return pedidoRepository.findAll().stream().map(PedidoResponseDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(UUID id) {
        return new PedidoResponseDTO(pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não localizado.")));
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarHistoricoCliente(UUID clienteId) {
        return pedidoRepository.findAll().stream()
                .filter(p -> p.getCliente() != null && p.getCliente().getId().equals(clienteId))
                .map(PedidoResponseDTO::new)
                .toList();
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

    // ==========================================
    // 💰 ROTINAS FINANCEIRAS PARA ADICIONAIS DE COMBO
    // ==========================================

    @Transactional
    public PedidoResponseDTO recalcularTotalPedido(UUID pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido informado não localizado."));

        // 1. Calcular o total dos ItemPedido (incluindo seus próprios adicionais)
        BigDecimal totalItensPedido = pedido.getItens().stream()
                .map(item -> {
                    BigDecimal precoBaseItem = item.getPrecoUnitario(); // Já inclui o preço do produto + adicionais de ItemPedido na criação
                    // Se o precoUnitario não incluir adicionais na criação, a lógica abaixo seria necessária:
                    // BigDecimal precoAdicionaisItem = item.getAdicionais().stream()
                    //         .map(Adicional::getPreco)
                    //         .reduce(BigDecimal.ZERO, BigDecimal::add);
                    // return precoBaseItem.add(precoAdicionaisItem).multiply(BigDecimal.valueOf(item.getQuantidade()));
                    return precoBaseItem.multiply(BigDecimal.valueOf(item.getQuantidade()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Calcular o acréscimo dos adicionais dos ItemCombo
        BigDecimal totalAdicionaisItemCombo = calcularTotalAdicionaisItensCombo(pedido);

        // 3. Somar todos os totais
        BigDecimal novoTotalPedido = totalItensPedido.add(totalAdicionaisItemCombo);

        pedido.setTotal(novoTotalPedido);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        PedidoResponseDTO response = new PedidoResponseDTO(pedidoSalvo);
        messagingTemplate.convertAndSend("/topic/caixa", response);
        return response;
    }

    /**
     * Calcula o valor total dos adicionais vinculados aos ItemCombo de um Pedido.
     * Esta lógica é aditiva e não interfere no cálculo base dos ItemPedido.
     *
     * @param pedido O Pedido a ser analisado.
     * @return O valor total dos adicionais dos ItemCombo.
     */
    private BigDecimal calcularTotalAdicionaisItensCombo(Pedido pedido) {
        BigDecimal totalAdicionaisCombos = BigDecimal.ZERO;

        for (ItemPedido itemPedido : pedido.getItens()) {
            // Verifica se o ItemPedido é um combo (assumindo que Produto.isCombo é carregado)
            // A auditoria identificou que ItemPedido.produto.isCombo não é diretamente exposto no DTO,
            // mas a entidade ItemPedido tem o Produto associado.
            if (itemPedido.getProduto() != null && itemPedido.getProduto().getIsCombo()) {
                List<ItemCombo> itensCombo = itemComboRepository.findByItemPedidoId(itemPedido.getId());

                for (ItemCombo itemCombo : itensCombo) {
                    BigDecimal precoAdicionaisItemCombo = itemCombo.getAdicionais().stream()
                            .map(Adicional::getPreco)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Regra: O adicional selecionado em um ItemCombo aplica-se a TODA a quantidade daquele ItemCombo.
                    totalAdicionaisCombos = totalAdicionaisCombos.add(
                            precoAdicionaisItemCombo.multiply(BigDecimal.valueOf(itemCombo.getQuantidade()))
                    );
                }
            }
        }
        return totalAdicionaisCombos;
    }

    // NOVO MÉTODO: Criar snapshots de ItemCombo para um ItemPedido individual
    private void criarSnapshotsDoCombo(ItemPedido itemPedido) {
        if (itemPedido == null
                || itemPedido.getProduto() == null
                || itemPedido.getId() == null) {
            return;
        }

        // Verifica se o produto do ItemPedido é um combo
        if (!Boolean.TRUE.equals(itemPedido.getProduto().getIsCombo())) {
            return;
        }

        // Proteção contra duplicidade: verifica se já existem ItemCombos para este ItemPedido
        List<ItemCombo> existentes = itemComboRepository.findByItemPedidoId(itemPedido.getId());
        if (!existentes.isEmpty()) {
            return;
        }

        // Busca a composição do combo
        List<ComboProduto> composicao = comboProdutoRepository.findByComboId(itemPedido.getProduto().getId());

        if (composicao.isEmpty()) {
            return; // Combo sem composição, apenas retorna
        }

        // Cria os snapshots de ItemCombo
        List<ItemCombo> snapshots = composicao.stream()
                .map(config -> {
                    Produto produtoInterno = config.getProduto();
                    ItemCombo itemCombo = new ItemCombo();

                    itemCombo.setItemPedido(itemPedido);
                    itemCombo.setProdutoId(produtoInterno.getId());
                    itemCombo.setNomeProduto(produtoInterno.getNome());
                    itemCombo.setQuantidade(config.getQuantidade()); // Usa a quantidade da composição do combo
                    itemCombo.setPrecoUnitario(produtoInterno.getPreco());

                    return itemCombo;
                })
                .collect(Collectors.toList());

        // Persiste todos os snapshots de uma vez
        itemComboRepository.saveAll(snapshots);
    }

    // NOVO MÉTODO: Processar todos os ItemPedidos de um Pedido para criar snapshots de combos
    private void criarSnapshotsDosCombos(Pedido pedido) {
        if (pedido == null || pedido.getItens() == null) {
            return;
        }

        pedido.getItens().forEach(this::criarSnapshotsDoCombo);
    }
}