package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.repositories.CarrinhoRepository;
import com.paullomaggio.estevaoLanches.repositories.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class PedidoService {

    @Autowired
    private CarrinhoRepository carrinhoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Transactional
    public PedidoResponseDTO finalizarPedido(CheckoutRequestDTO dto) {
        Carrinho carrinho = carrinhoRepository.findByClienteId(dto.clienteId())
                .orElseThrow(() -> new RuntimeException("Carrinho não encontrado para este cliente!"));

        if (carrinho.getItens().isEmpty()) {
            throw new RuntimeException("O carrinho está vazio!");
        }

        // 1. Cria o Pedido Base
        Pedido pedido = new Pedido();
        pedido.setCliente(carrinho.getCliente());
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setTipo(dto.tipo());
        pedido.setEnderecoEntrega(dto.enderecoEntrega());
        pedido.setNumeroMesa(dto.numeroMesa());
        pedido.setObservacaoGeral(dto.observacaoGeral());
        pedido.setDataHora(LocalDateTime.now());

        BigDecimal totalPedido = BigDecimal.ZERO;

        // 2. Transforma ItemCarrinho em ItemPedido
        for (ItemCarrinho itemCarrinho : carrinho.getItens()) {
            ItemPedido itemPedido = new ItemPedido();
            itemPedido.setPedido(pedido);
            itemPedido.setProduto(itemCarrinho.getProduto());
            itemPedido.setQuantidade(itemCarrinho.getQuantidade());
            itemPedido.setObservacaoItem(itemCarrinho.getObservacao());

            BigDecimal precoAtual = itemCarrinho.getProduto().getPreco();
            itemPedido.setPrecoUnitario(precoAtual);

            BigDecimal subtotal = precoAtual.multiply(BigDecimal.valueOf(itemCarrinho.getQuantidade()));
            totalPedido = totalPedido.add(subtotal);

            pedido.getItens().add(itemPedido);
        }

        pedido.setTotal(totalPedido);
        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        // 3. Limpa o carrinho
        carrinho.getItens().clear();
        carrinhoRepository.save(carrinho);

        return new PedidoResponseDTO(pedidoSalvo);
    }
}