package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record PedidoResponseDTO(
        UUID id,
        String clienteNome,
        LocalDateTime dataHora,
        StatusPedido status,
        TipoPedido tipo,
        BigDecimal total,
        String enderecoEntrega,
        Integer numeroMesa,
        List<ItemPedidoResponseDTO> itens
) {
    public PedidoResponseDTO(Pedido pedido) {
        this(
                pedido.getId(),
                pedido.getCliente().getNome(),
                pedido.getDataHora(),
                pedido.getStatus(),
                pedido.getTipo(),
                pedido.getTotal(),
                pedido.getEnderecoEntrega(),
                pedido.getNumeroMesa(),
                pedido.getItens().stream().map(ItemPedidoResponseDTO::new).collect(Collectors.toList())
        );
    }
}