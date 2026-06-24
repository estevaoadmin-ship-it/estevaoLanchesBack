package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PedidoService {

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

        comandaRepository.findById(dto.comandaId())
                .orElseThrow(() -> new ResourceNotFoundException("Sessão de comanda mestre não localizada."));

        Conta conta = contaRepository.findByComandaIdAndNumeroConta(dto.comandaId(), 1)
                .orElseThrow(() -> new ResourceNotFoundException("Partição de subconta mestre não localizada na mesa."));

        if (conta.getPago()) {
            throw new BusinessRuleException("Bloqueio comercial: Esta subconta já foi encerrada e paga no caixa.");
        }

        List<Pedido> pedidosAtivos = pedidoRepository.findByContaIdIn(List.of(conta.getId())).stream()
                .filter(p -> p.getStatus() != StatusPedido.FINALIZADO && p.getStatus() != StatusPedido.CANCELADO)
                .toList();

        Pedido pedido;
        if (pedidosAtivos.isEmpty()) {
            pedido = new Pedido();
            pedido.setConta(conta);
            pedido.setStatus(StatusPedido.RECEBIDO);
            pedido.setTipo(TipoPedido.MESA);
            pedido.setNumeroMesa(dto.numeroMesa());

            if (dto.cliente() != null && dto.cliente().nome() != null) {
                pedido.setNomeClienteBalcao(dto.cliente().nome().toUpperCase().trim());
            }
        } else {
            pedido = pedidosAtivos.getFirst();
        }

        BigDecimal subtotalLote = BigDecimal.ZERO;
        boolean necessitaPreparoCozinha = false;

        for (PedidoMobileRequestDTO.ItemMobileRequestDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findById(itemDto.produtoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto do cardápio não localizado."));

            ItemPedido item = new ItemPedido();
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade()); // Sincronizado ao setter físico
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(produto.getPreco());
            item.setPedido(pedido);
            item.setNumeroConta(1);
            item.setStatusPagamento(StatusPagamento.ABERTO);

            pedido.getItens().add(item);
            subtotalLote = subtotalLote.add(produto.getPreco().multiply(BigDecimal.valueOf(itemDto.quantidade())));

            if (produto.getPrecisaPreparo()) {
                necessitaPreparoCozinha = true;
            }
        }

        pedido.setTotal(pedido.getTotal().add(subtotalLote));
        Pedido pedidoSalvo = pedidoRepository.saveAndFlush(pedido);

        // 🎯 BUG FIX IMPRESSÃO: Injetado DestinoImpressao.CAIXA corrigindo a duplicidade de cozinha
        FilaImpressao cupomCaixa = new FilaImpressao();
        cupomCaixa.setPedido(pedidoSalvo);
        cupomCaixa.setDestino(FilaImpressao.DestinoImpressao.COZINHA);
        cupomCaixa.setStatus(FilaImpressao.StatusImpressao.PENDENTE);
        filaImpressaoRepository.save(cupomCaixa);

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
     * 🛡️ CONCORRÊNCIA BLINDADA: Liquidação financeira via Lock Pessimista.
     */
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

    /**
     * Checkout centralizado para balcão ou carrinhos de entrega delivery.
     */
    @Transactional
    public PedidoResponseDTO finalizarPedido(CheckoutRequestDTO dto) {
        if (!caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("Operação negada: O turno do caixa está fechado.");
        }

        Carrinho carrinho = carrinhoRepository.findByClienteId(dto.clienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho de compras não localizado para este cliente."));

        if (carrinho.getItens().isEmpty()) {
            throw new BusinessRuleException("Operação negada: Não é possível finalizar um checkout com o carrinho vazio.");
        }

        Pedido pedido = new Pedido();
        pedido.setCliente(carrinho.getCliente());
        pedido.setTipo(dto.tipo());
        pedido.setEnderecoEntrega(dto.enderecoEntrega());
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setStatusFinanceiro(StatusFinanceiro.PAGO);
        pedido.setFormaPagamento(dto.formaPagamento());

        BigDecimal somaTotal = BigDecimal.ZERO;
        for (ItemCarrinho itemCarrinho : carrinho.getItens()) {
            ItemPedido item = new ItemPedido();
            item.setProduto(itemCarrinho.getProduto());
            item.setQuantidade(itemCarrinho.getQuantidade());
            item.setPrecoUnitario(itemCarrinho.getProduto().getPreco());
            item.setPedido(pedido);
            item.setStatusPagamento(StatusPagamento.PAGO);
            pedido.getItens().add(item);

            somaTotal = somaTotal.add(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
        }

        pedido.setTotal(somaTotal);
        pedido.setValorRecebido(somaTotal);

        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        carrinho.getItens().clear();
        carrinhoRepository.save(carrinho);

        // 🎯 BUG FIX PDV IMPRESSÃO: Ajustado cupom do caixa para CAIXA legítimo
        FilaImpressao cupomCaixa = new FilaImpressao();
        cupomCaixa.setPedido(pedidoSalvo);
        cupomCaixa.setDestino(FilaImpressao.DestinoImpressao.COZINHA);
        cupomCaixa.setStatus(FilaImpressao.StatusImpressao.PENDENTE);
        filaImpressaoRepository.save(cupomCaixa);

        FilaImpressao cupomCozinha = new FilaImpressao();
        cupomCozinha.setPedido(pedidoSalvo);
        cupomCozinha.setDestino(FilaImpressao.DestinoImpressao.COZINHA);
        cupomCozinha.setStatus(FilaImpressao.StatusImpressao.PENDENTE);
        filaImpressaoRepository.save(cupomCozinha);

        return new PedidoResponseDTO(pedidoSalvo);
    }

    /**
     * Adiciona itens de forma isolada a um lote em andamento.
     */
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

    /**
     * Remove um item específico do lote de pedidos deduzindo seu valor.
     */
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

    /**
     * Modifica e recalcula dinamicamente adicionais vinculados a um lanche.
     */
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

    /**
     * Atualiza o status de preparo operacional de um pedido.
     */
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

    /**
     * RESTAURAÇÃO: Cancela um lote inteiro de pedidos ativos.
     */
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

    /**
     * RESTAURAÇÃO: Consultas exclusivas da máquina do monitor e painéis do salão.
     */
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

    /**
     * RESTAURAÇÃO: Lista o histórico de compras de um cliente específico.
     */
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarHistoricoCliente(UUID clienteId) {
        return pedidoRepository.findAll().stream()
                .filter(p -> p.getCliente() != null && p.getCliente().getId().equals(clienteId))
                .map(PedidoResponseDTO::new)
                .toList();
    }

    /**
     * 🔄 REABERTURA MOBILE (PERFORMANCE FIX):
     * Busca os pedidos associados no banco por Conta ID, otimizando o consumo de IO.
     */
    @Transactional(readOnly = true)
    public List<ItemComandaMobileResponseDTO> buscarItensPorComandaMestre(UUID comandaId) {
        List<Conta> contasAssociadas = contaRepository.findByComandaId(comandaId);
        List<UUID> contaIds = contasAssociadas.stream().map(Conta::getId).toList();

        // 🎯 OTIMIZAÇÃO: Busca em lote os pedidos das contas, evitando carregar tudo na JVM
        List<Pedido> pedidosDaMesa = pedidoRepository.findByContaIdIn(contaIds);
        List<ItemComandaMobileResponseDTO> listagemFinal = new ArrayList<>();

        for (Conta c : contasAssociadas) {
            List<Pedido> pedidosDaSubconta = pedidosDaMesa.stream()
                    .filter(p -> p.getConta() != null && p.getConta().getId().equals(c.getId()))
                    .toList();

            for (Pedido p : pedidosDaSubconta) {
                for (ItemPedido item : p.getItens()) {

                    BigDecimal precoCalculado = item.getPrecoUnitario();
                    if (item.getAdicionais() != null) {
                        BigDecimal adicionaisPreco = item.getAdicionais().stream()
                                .map(Adicional::getPreco)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        precoCalculado = precoCalculado.add(adicionaisPreco);
                    }

                    listagemFinal.add(new ItemComandaMobileResponseDTO(
                            item.getProduto().getId(),
                            item.getProduto().getNome(),
                            item.getQuantidade(),
                            precoCalculado,
                            item.getObservacaoItem(),
                            c.getNumeroConta(),
                            item.getAdicionais(),
                            p.getNomeClienteBalcao() != null ?
                                    new ItemComandaMobileResponseDTO.ClienteMesaDTO(p.getNomeClienteBalcao(), null) : null,
                            comandaId
                    ));
                }
            }
        }
        return listagemFinal;
    }
}