package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CheckoutRequestDTO(
        UUID clienteId,

        @NotNull(message = "O tipo do pedido é obrigatório")
        TipoPedido tipo,

        String enderecoEntrega,
        Integer numeroMesa,
        String observacaoGeral,
        String nomeClienteBalcao,
        String telefoneClienteBalcao,

        // REMOVIDO O @NotNull
        FormaPagamento formaPagamento,

        BigDecimal valorRecebido,

        @NotEmpty(message = "O pedido deve conter pelo menos um item.")
        @Valid
        List<ItemPedidoRequestDTO> itens
) {}