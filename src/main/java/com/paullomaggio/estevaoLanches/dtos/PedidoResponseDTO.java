package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record PedidoResponseDTO(
        UUID id,
        String numeroPedido,
        String clienteNome,
        LocalDateTime dataHora,
        StatusPedido status,
        StatusFinanceiro statusFinanceiro, // NOVO
        FormaPagamento formaPagamento,     // NOVO
        TipoPedido tipo,
        BigDecimal total,
        String enderecoEntrega,
        Integer numeroMesa,
        String observacaoGeral,
        List<ItemPedidoResponseDTO> itens
) {
    public PedidoResponseDTO(Pedido pedido) {
        this(
                pedido.getId(),
                pedido.getNumeroPedido(),
                pedido.getCliente() != null ? pedido.getCliente().getNome() : pedido.getNomeClienteBalcao(),
                pedido.getDataHora(),
                pedido.getStatus(),
                pedido.getStatusFinanceiro(), // NOVO
                pedido.getFormaPagamento(),   // NOVO
                pedido.getTipo(),
                pedido.getTotal(),
                pedido.getEnderecoEntrega(),
                pedido.getNumeroMesa(),
                pedido.getObservacaoGeral(),
                pedido.getItens() != null ? pedido.getItens().stream().map(ItemPedidoResponseDTO::new).collect(Collectors.toList()) : List.of()
        );
    }
}