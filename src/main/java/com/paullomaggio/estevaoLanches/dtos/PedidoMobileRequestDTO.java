package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PedidoMobileRequestDTO(
        UUID comandaId,

        @NotNull(message = "O número da mesa é obrigatório")
        Integer numeroMesa,

        @NotNull(message = "A conta filha é obrigatória")
        Integer contaFilha,

        @Valid
        ClienteMobileRequestDTO cliente,

        @NotNull(message = "A comanda não pode ser enviada sem itens")
        List<ItemMobileRequestDTO> itens
) {}