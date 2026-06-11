package com.paullomaggio.estevaoLanches.services;

import com.paullomaggio.estevaoLanches.dtos.CheckoutRequestDTO;
import com.paullomaggio.estevaoLanches.dtos.PedidoResponseDTO;
import com.paullomaggio.estevaoLanches.entities.Carrinho;
import com.paullomaggio.estevaoLanches.entities.ItemCarrinho;
import com.paullomaggio.estevaoLanches.entities.ItemPedido;
import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusCaixa;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.repositories.CaixaRepository;
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

    @Autowired
    private CaixaRepository caixaRepository; // <-- Injeção do controle de abertura do dia

    @Transactional
    public PedidoResponseDTO finalizarPedido(CheckoutRequestDTO dto) {
        // 0. TRAVA DE SEGURANÇA: Bloqueia as vendas se o estabelecimento estiver fechado
        if (!caixaRepository.existsByStatus(StatusCaixa.ABERTO)) {
            throw new RuntimeException("O estabelecimento está fechado no momento. Abra o caixa no painel para iniciar as vendas!");
        }

        // 1. Busca o Carrinho
        Carrinho carrinho = carrinhoRepository.findByClienteId(dto.clienteId())
                .orElseThrow(() -> new RuntimeException("Carrinho não encontrado para este cliente!"));

        if (carrinho.getItens().isEmpty()) {
            throw new RuntimeException("O carrinho está vazio!");
        }

        // 2. Cria o Pedido Base
        Pedido pedido = new Pedido();
        pedido.setCliente(carrinho.getCliente());
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setTipo(dto.tipo());
        pedido.setEnderecoEntrega(dto.enderecoEntrega());
        pedido.setNumeroMesa(dto.numeroMesa());
        pedido.setObservacaoGeral(dto.observacaoGeral());
        pedido.setDataHora(LocalDateTime.now());

        BigDecimal totalPedido = BigDecimal.ZERO;

        // 3. Transforma ItemCarrinho em ItemPedido (Mantendo preço histórico)
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

        // 4. Limpa o carrinho do cliente
        carrinho.getItens().clear();
        carrinhoRepository.save(carrinho);

        return new PedidoResponseDTO(pedidoSalvo);
    }
}