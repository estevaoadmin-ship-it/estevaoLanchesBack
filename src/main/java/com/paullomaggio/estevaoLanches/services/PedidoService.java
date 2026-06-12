package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.ItemPedidoRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoStatusRequestDTO;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.entities.Produto;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.exceptions.BusinessRuleException;
import com.paullomaggio.estevaoLanches.exceptions.ResourceNotFoundException;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import com.paullomaggio.estevaoLanches.repositories.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Transactional
    public PedidoResponseDTO finalizarPedido(CheckoutRequestDTO dto) {
        if (!caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new BusinessRuleException("O estabelecimento está fechado. Abra o caixa para iniciar as vendas!");
        }

        Carrinho carrinho = carrinhoRepository.findByClienteId(dto.clienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Carrinho não encontrado!"));

        if (carrinho.getItens().isEmpty()) {
            throw new BusinessRuleException("O carrinho está vazio!");
        }

        Pedido pedido = new Pedido();
        pedido.setCliente(carrinho.getCliente());
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setTipo(dto.tipo());
        pedido.setEnderecoEntrega(dto.enderecoEntrega());
        pedido.setNumeroMesa(dto.numeroMesa());
        pedido.setObservacaoGeral(dto.observacaoGeral());
        pedido.setDataHora(LocalDateTime.now());

        BigDecimal totalPedido = BigDecimal.ZERO;

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

        pedido.setTotal(totalPedido);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        carrinho.getItens().clear();
        carrinhoRepository.save(carrinho);

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