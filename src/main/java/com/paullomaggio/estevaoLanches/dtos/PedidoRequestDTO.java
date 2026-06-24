package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * DTO unificado para criação e manipulação padrão de Pedidos (Retaguarda/Caixa).
 */
public record PedidoRequestDTO(
        @NotNull(message = "O tipo do pedido é obrigatório.")
        TipoPedido tipo,

        UUID contaId,
        UUID clienteId,
        Integer numeroMesa,
        String nomeClienteBalcao,
        String enderecoEntrega,
        String observacaoGeral,

        @NotEmpty(message = "O pedido deve conter ao menos um item.")
        @Valid
        List<ItemPedidoRequestDTO> itens
) {}