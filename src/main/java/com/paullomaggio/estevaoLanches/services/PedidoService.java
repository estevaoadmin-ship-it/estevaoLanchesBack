package com.paullomaggio.estevaoLanches.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final CarrinhoRepository carrinhoRepository;
    private final CaixaRepository caixaRepository;
    private final ProdutoRepository produtoRepository;
    private final AdicionalRepository adicionalRepository;
    private final FilaImpressaoRepository filaImpressaoRepository;
    private final ComandaRepository comandaRepository;
    private final ContaRepository contaRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 📱 FLUXO MOBILE: Processa a entrada de lotes enviados por garçons ou tablets.
     */
    @Transactional
    public PedidoResponseDTO processarPedidoMobile(PedidoMobileRequestDTO dto) {
        if (!caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Operação negada: O turno do caixa está fechado.");
        }

        Conta conta = contaRepository.findByComandaIdAndNumeroConta(dto.comandaId(), dto.numeroConta())
                .orElseGet(() -> {
                    Comanda comandaMestre = comandaRepository.findById(dto.comandaId())
                            .orElseThrow(() -> new ResourceNotFoundException("Sessão de comanda mestre não localizada."));

                    Conta novaConta = new Conta();
                    novaConta.setComanda(comandaMestre);
                    novaConta.setNumeroConta(dto.numeroConta());
                    novaConta.setValorTotal(BigDecimal.ZERO);
                    novaConta.setPago(false);

                    return contaRepository.save(novaConta);
                });

        if (conta.getPago()) {
            throw new BusinessRuleException("Bloqueio comercial: Esta subconta já foi encerrada e paga no caixa.");
        }

        // AUDITORIA 3
        log.info("==============================");
        log.info("CRIANDO PEDIDO - PedidoService.processarPedidoMobile()");
        log.info("==============================");
        log.info("Mesa Número: {}", dto.numeroMesa());
        log.info("Conta ID: {}", conta.getId());
        log.info("Conta Número: {}", conta.getNumeroConta());
        log.info("Itens recebidos no DTO: {}", dto.itens().size());

        for (var itemDto : dto.itens()) {
            log.info(
                "  Produto ID: {} | Quantidade: {} | Observação: {}",
                itemDto.produtoId(),
                itemDto.quantidade(),
                itemDto.observacao()
            );
        }
        log.info("==============================");


        Pedido pedido = new Pedido();
        pedido.setConta(conta); // AUDITORIA 9: Vinculando Pedido à Conta
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setTipo(TipoPedido.MESA);
        pedido.setNumeroMesa(dto.numeroMesa());
        pedido.setTotal(BigDecimal.ZERO);
        pedido.setItens(new ArrayList<>());

        // Lógica existente para preencher o nome do cliente, se fornecido no DTO
        if (dto.cliente() != null && dto.cliente().nome() != null) {
            pedido.setNomeClienteBalcao(dto.cliente().nome().toUpperCase().trim());
        }

        // Fallback: Se o nome do cliente não foi preenchido pela lógica acima, vincular o responsável da mesa
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
            item.setPedido(pedido); // AUDITORIA 9: Vinculando ItemPedido ao Pedido
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
        Pedido pedidoSalvo = pedidoRepository.saveAndFlush(pedido); // AUDITORIA 4: Pedido salvo aqui

        // AUDITORIA 4
        log.info("=============================");
        log.info("AUDITORIA 4 - PedidoService.processarPedidoMobile() - Pedido Salvo");
        log.info("=============================");
        log.info("Pedido salvo:");
        log.info("  ID: {}", pedidoSalvo.getId());
        log.info("  Conta ID: {}", pedidoSalvo.getConta().getId());
        log.info("  Mesa Número: {}", pedidoSalvo.getNumeroMesa());
        log.info("  Quantidade de itens no Pedido salvo: {}", pedidoSalvo.getItens().size());
        log.info("  Itens do Pedido salvo:");
        pedidoSalvo.getItens().forEach(item -> {
            log.info("    - UUID: {}", item.getId());
            log.info("      Produto: {}", item.getProduto().getNome());
            log.info("      Quantidade: {}", item.getQuantidade());
            log.info("      Valor Unitário: {}", item.getPrecoUnitario());
            log.info("      Valor Total Item: {}", item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
        });
        log.info("=============================");

        // AUDITORIA 5
        log.info("=============================");
        log.info("AUDITORIA 5 - PedidoService.processarPedidoMobile() - Verificando Pedido no Banco (findById)");
        log.info("=============================");
        Pedido pedidoBanco = pedidoRepository.findById(pedidoSalvo.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido salvo não encontrado no banco."));
        log.info("Pedido ID do banco: {}", pedidoBanco.getId());
        log.info("pedidoBanco.getItens().size(): {}", pedidoBanco.getItens().size());
        pedidoBanco.getItens().forEach(item -> {
            log.info("    - Item ID: {}, Produto: {}, Quantidade: {}", item.getId(), item.getProduto().getNome(), item.getQuantidade());
        });
        log.info("=============================");

        // AUDITORIA FINAL - CONTA EM MEMÓRIA
        log.info("==========================================");
        log.info("AUDITORIA FINAL - CONTA EM MEMÓRIA");
        log.info("==========================================");

        log.info("Conta ID: {}", conta.getId());
        log.info("Pedidos na memória: {}", conta.getPedidos().size());

        for (Pedido p : conta.getPedidos()) {
            log.info("Pedido ID: {}", p.getId());

            if (p.getItens() != null) {
                log.info("Quantidade de itens: {}", p.getItens().size());

                for (ItemPedido item : p.getItens()) {
                    log.info(
                        "Produto={} Quantidade={}",
                        item.getProduto().getNome(),
                        item.getQuantidade()
                    );
                }
            }
        }
        log.info("==========================================");

        // Recarregue a Conta do banco.
        Conta contaBanco = contaRepository.findById(conta.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada após salvar pedido."));

        // AUDITORIA FINAL - CONTA RECARREGADA DO BANCO
        log.info("==========================================");
        log.info("AUDITORIA FINAL - CONTA RECARREGADA DO BANCO");
        log.info("==========================================");

        log.info("Conta ID: {}", contaBanco.getId());
        log.info("Pedidos no banco: {}", contaBanco.getPedidos().size());

        for (Pedido p : contaBanco.getPedidos()) {
            log.info("Pedido ID: {}", p.getId());

            if (p.getItens() != null) {
                log.info("Quantidade de itens: {}", p.getItens().size());

                for (ItemPedido item : p.getItens()) {
                    log.info(
                        "Produto={} Quantidade={}",
                        item.getProduto().getNome(),
                        item.getQuantidade()
                    );
                }
            }
        }
        log.info("==========================================");


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

        BigDecimal novoTotal = pedido.getItens().stream()
                .map(i -> {
                    BigDecimal base = i.getPrecoUnitario();
                    if (i.getAdicionais() != null) {
                        BigDecimal adicionaisPreco = i.getAdicionais().stream()
                                .map(Adicional::getPreco)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        base = base.add(adicionaisPreco);
                    }
                    return base.multiply(BigDecimal.valueOf(i.getQuantidade()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        pedido.setTotal(novoTotal);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        PedidoResponseDTO response = new PedidoResponseDTO(pedidoSalvo);
        messagingTemplate.convertAndSend("/topic/caixa", response);
        return response;
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
}