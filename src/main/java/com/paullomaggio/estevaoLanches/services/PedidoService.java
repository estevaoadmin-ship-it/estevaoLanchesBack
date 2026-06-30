package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.*;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.*;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException; // Importar AccessDeniedException
import org.springframework.security.core.Authentication; // Importar Authentication
import org.springframework.security.core.context.SecurityContextHolder; // Importar SecurityContextHolder
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

        // 🎯 FIX 404 & 409 CONTA FILHA: Se a subconta (Ex: Conta 2) não existir, ela é instanciada herdando
        // obrigatoriamente o mesmo Cliente relacional da Conta Mãe (Conta 1) daquela mesa.
        Conta conta = contaRepository.findByComandaIdAndNumeroConta(dto.comandaId(), dto.numeroConta())
                .orElseGet(() -> {
                    Comanda comandaMestre = comandaRepository.findById(dto.comandaId())
                            .orElseThrow(() -> new ResourceNotFoundException("Sessão de comanda mestre não localizada."));

                    Conta novaConta = new Conta();
                    novaConta.setComanda(comandaMestre);
                    novaConta.setNumeroConta(dto.numeroConta());
                    novaConta.setValorTotal(BigDecimal.ZERO);
                    novaConta.setPago(false);

                    // Envelopamento Automático: Herda o cliente da partição principal (Conta 1)
                    contaRepository.findByComandaIdAndNumeroConta(dto.comandaId(), 1)
                            .ifPresent(contaMae -> novaConta.setCliente(contaMae.getCliente()));

                    return contaRepository.save(novaConta);
                });

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

        // 🎯 FIX CRÍTICO 409: Satisfeita a restrição de integridade relacional 'cliente_id' NOT NULL da tabela Pedido
        if (conta.getCliente() != null) {
            pedido.setCliente(conta.getCliente());
        }

        if (dto.cliente() != null && dto.cliente().nome() != null) {
            pedido.setNomeClienteBalcao(dto.cliente().nome().toUpperCase().trim());
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
            // Adicionada validação para preço final do item (Correção RB019)
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
        pedido.setItens(new ArrayList<>());

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

        // Fila de impressão (movido para antes da limpeza do carrinho)
        FilaImpressao cupomCozinha = new FilaImpressao();
        cupomCozinha.setPedido(pedidoSalvo);
        cupomCozinha.setDestino(FilaImpressao.DestinoImpressao.COZINHA);
        cupomCozinha.setStatus(FilaImpressao.StatusImpressao.PENDENTE);
        filaImpressaoRepository.save(cupomCozinha);

        // Limpeza do carrinho e salvamento (movido para depois da fila de impressão)
        carrinho.getItens().clear();
        carrinhoRepository.save(carrinho);

        return new PedidoResponseDTO(pedidoSalvo);
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

                String nomeCli = p.getNomeClienteBalcao();
                if ((nomeCli == null || nomeCli.isBlank()) && c.getCliente() != null) {
                    nomeCli = c.getCliente().getNome();
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
}