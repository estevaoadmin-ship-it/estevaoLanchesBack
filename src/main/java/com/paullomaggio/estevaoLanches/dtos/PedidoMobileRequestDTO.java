package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PedidoMobileRequestDTO(
        @NotNull(message = "O ID da comanda mestre é obrigatório.")
        UUID comandaId,

        Integer numeroMesa,
        Integer contaFilha,
        ClienteMobileDTO cliente,
        List<ItemMobileRequestDTO> itens
) {
        public record ClienteMobileDTO(String nome, String telefone) {}

        public record ItemMobileRequestDTO(
                UUID produtoId,
                int quantidade,
                String observacao,
                List<UUID> adicionaisIds
        ) {}
}