package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import java.util.UUID;

public record CheckoutRequestDTO(
        UUID clienteId, // <-- Esse carinha tinha ficado de fora!
        TipoPedido tipo,
        String enderecoEntrega,
        Integer numeroMesa,
        String observacaoGeral
) {}