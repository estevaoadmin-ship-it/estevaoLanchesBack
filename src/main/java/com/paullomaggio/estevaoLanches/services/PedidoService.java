package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemPedidoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoStatusRequestDTO;
import com.paullomaggio.estevaoLanches.entities.*;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    @Autowired
    private CarrinhoRepository carrinhoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Transactional
    public PedidoResponseDTO finalizarPedido(CheckoutRequestDTO dto) {
        if (!caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("O estabelecimento está fechado. Abra o caixa para iniciar as vendas!");
        }

        Pedido pedido = new Pedido();
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setTipo(dto.tipo());
        pedido.setEnderecoEntrega(dto.enderecoEntrega());
        pedido.setNumeroMesa(dto.numeroMesa());
        pedido.setObservacaoGeral(dto.observacaoGeral());
        pedido.setDataHora(LocalDateTime.now());

        pedido.setFormaPagamento(dto.formaPagamento());
        pedido.setValorRecebido(dto.valorRecebido());
        pedido.setNomeClienteBalcao(dto.nomeClienteBalcao());

        if (pedido.getItens() == null) {
            pedido.setItens(new ArrayList<>());
        }

        BigDecimal totalPedido = BigDecimal.ZERO;

        // BIFURCAÇÃO: Se vier da lista direta (PDV), pula verificação de carrinho persistido
        if (dto.itens() != null && !dto.itens().isEmpty()) {

            if (dto.clienteId() != null) {
                Cliente cliente = clienteRepository.findById(dto.clienteId())
                        .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado!"));
                pedido.setCliente(cliente);
            } else {
                pedido.setCliente(null);
            }

            for (var itemDto : dto.itens()) {
                Produto produto = produtoRepository.findById(itemDto.produtoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDto.produtoId()));

                ItemPedido itemPedido = new ItemPedido();
                itemPedido.setPedido(pedido);
                itemPedido.setProduto(produto);
                itemPedido.setQuantidade(itemDto.quantidade());
                itemPedido.setObservacaoItem(itemDto.observacao());
                itemPedido.setPrecoUnitario(produto.getPreco());

                BigDecimal subtotal = itemPedido.getPrecoUnitario().multiply(BigDecimal.valueOf(itemPedido.getQuantidade()));
                totalPedido = totalPedido.add(subtotal);
                pedido.getItens().add(itemPedido);
            }
        } else {
            // Fluxo nativo via App Delivery (Lê carrinho temporário)
            if (dto.clienteId() == null) {
                throw new BusinessRuleException("Para recuperar o carrinho do banco, o clienteId é obrigatório!");
            }

            Carrinho carrinho = carrinhoRepository.findByClienteId(dto.clienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado!"));

            if (carrinho.getItens().isEmpty()) {
                throw new BusinessRuleException("O carrinho está vazio!");
            }

            pedido.setCliente(carrinho.getCliente());

            for (ItemCarrinho itemCarrinho : carrinho.getItens()) {
                ItemPedido itemPedido = new ItemPedido();
                itemPedido.setPedido(pedido);
                itemPedido.setProduto(itemCarrinho.getProduto());
                itemPedido.setQuantidade(itemCarrinho.getQuantidade());
                itemPedido.setObservacaoItem(itemCarrinho.getObservacao());
                itemPedido.setPrecoUnitario(itemCarrinho.getProduto().getPreco());

                BigDecimal subtotal = itemPedido.getPrecoUnitario().multiply(BigDecimal.valueOf(itemPedido.getQuantidade()));
                totalPedido = totalPedido.add(subtotal);
                pedido.getItens().add(itemPedido);
            }

            carrinho.getItens().clear();
            carrinhoRepository.save(carrinho);
        }

        pedido.setTotal(totalPedido);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        return new PedidoResponseDTO(pedidoSalvo);
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPorId(UUID id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        return new PedidoResponseDTO(pedido);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarTodos() {
        return pedidoRepository.findAll().stream()
                .map(PedidoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarHistoricoCliente(UUID clienteId) {
        return pedidoRepository.findByClienteIdOrderByDataHoraDesc(clienteId).stream()
                .map(PedidoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> listarPedidosAtivosMonitor() {
        List<StatusPedido> ativos = Arrays.asList(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARO, StatusPedido.PRONTO, StatusPedido.EM_ROTA);
        return pedidoRepository.findByStatusInOrderByDataHoraAsc(ativos).stream()
                .map(PedidoResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public PedidoResponseDTO atualizarStatus(UUID id, PedidoStatusRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new BusinessRuleException("Não é possível alterar o status de um pedido Finalizado ou Cancelado.");
        }

        pedido.setStatus(dto.status());
        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO cancelarPedido(UUID id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO) {
            throw new BusinessRuleException("Pedidos já entregues (Finalizados) não podem ser cancelados.");
        }

        pedido.setStatus(StatusPedido.CANCELADO);
        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO adicionarItemPedido(UUID pedidoId, ItemPedidoRequestDTO dto) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.EM_ROTA) {
            throw new BusinessRuleException("Não é possível adicionar itens a um pedido que já está em rota, finalizado ou cancelado.");
        }

        Produto produto = produtoRepository.findById(dto.produtoId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado no cardápio."));

        ItemPedido novoItem = new ItemPedido();
        novoItem.setPedido(pedido);
        novoItem.setProduto(produto);
        novoItem.setQuantidade(dto.quantidade());
        novoItem.setPrecoUnitario(produto.getPreco());
        novoItem.setObservacaoItem(dto.observacao());

        pedido.getItens().add(novoItem);

        BigDecimal valorAdicional = produto.getPreco().multiply(BigDecimal.valueOf(dto.quantidade()));
        pedido.setTotal(pedido.getTotal().add(valorAdicional));

        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public PedidoResponseDTO removerItemPedido(UUID pedidoId, UUID itemId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));

        if (pedido.getStatus() == StatusPedido.FINALIZADO || pedido.getStatus() == StatusPedido.CANCELADO || pedido.getStatus() == StatusPedido.EM_ROTA) {
            throw new BusinessRuleException("Não é possível alterar os itens de um pedido que já está em rota, finalizado ou cancelado.");
        }

        ItemPedido itemParaRemover = pedido.getItens().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item não encontrado nesta comanda."));

        BigDecimal valorSubtrair = itemParaRemover.getPrecoUnitario().multiply(BigDecimal.valueOf(itemParaRemover.getQuantidade()));
        pedido.setTotal(pedido.getTotal().subtract(valorSubtrair));

        pedido.getItens().remove(itemParaRemover);

        return new PedidoResponseDTO(pedidoRepository.save(pedido));
    }

    @Transactional
    public void excluirFisicamente(UUID id) {
        throw new BusinessRuleException("Por razões financeiras e de auditoria, pedidos não podem ser excluídos do banco de dados. Utilize a função de Cancelamento.");
    }
}