package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckoutDeliveryRequestDTO(
        @NotNull UUID clienteId,
        @NotBlank String enderecoEntrega,
        @NotNull FormaPagamento formaPagamento,
        String observacao,
        String cupom
) {}