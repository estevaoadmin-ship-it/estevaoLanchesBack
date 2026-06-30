package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * DTO unificado para criação e manipulação padrão de Pedidos (Retaguarda/Caixa).
 */
@Schema(description = "DTO para criação e manipulação de pedidos (Retaguarda/Caixa)")
public record PedidoRequestDTO(
        @Schema(
            description = "Tipo do pedido",
            allowableValues = {"MESA", "BALCAO", "DELIVERY"},
            example = "MESA",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O tipo do pedido é obrigatório.")
        TipoPedido tipo,

        @Schema(
            description = "ID da comanda (apenas para pedidos de mesa)",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID contaId,
        @Schema(
            description = "ID do cliente (apenas para pedidos de delivery)",
            format = "uuid",
            example = "f1e2d3c4-b5a6-0987-6543-210fedcba987"
        )
        UUID clienteId,
        @Schema(
            description = "Número da mesa (apenas para pedidos de mesa)",
            example = "5"
        )
        Integer numeroMesa,
        @Schema(
            description = "Nome do cliente (apenas para pedidos de balcão)",
            example = "João da Silva"
        )
        String nomeClienteBalcao,
        @Schema(
            description = "Endereço de entrega (apenas para pedidos de delivery)",
            example = "Rua Exemplo, 123, Bairro, Cidade - SP"
        )
        String enderecoEntrega,
        @Schema(
            description = "Observação geral do pedido",
            example = "Sem cebola, por favor."
        )
        String observacaoGeral,

        @Schema(description = "Lista de itens do pedido")
        @NotEmpty(message = "O pedido deve conter ao menos um item.")
        @Valid
        List<ItemPedidoRequestDTO> itens
) {}