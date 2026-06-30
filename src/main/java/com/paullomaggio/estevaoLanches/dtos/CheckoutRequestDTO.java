package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "DTO para checkout de um pedido")
public record CheckoutRequestDTO(
        @Schema(
            description = "ID do cliente que está realizando o checkout (opcional, para clientes logados)",
            format = "uuid",
            example = "f1e2d3c4-b5a6-0987-6543-210fedcba987"
        )
        UUID clienteId,

        @Schema(
            description = "Tipo do pedido",
            allowableValues = {"MESA", "BALCAO", "DELIVERY"},
            example = "DELIVERY",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "O tipo do pedido é obrigatório")
        TipoPedido tipo,

        @Schema(
            description = "Endereço de entrega para pedidos do tipo DELIVERY",
            example = "Rua das Flores, 123, Centro, Cidade - UF"
        )
        String enderecoEntrega,
        @Schema(
            description = "Número da mesa para pedidos do tipo MESA",
            example = "7"
        )
        Integer numeroMesa,
        @Schema(
            description = "Observações gerais sobre o pedido",
            example = "Sem cebola, por favor."
        )
        String observacaoGeral,
        @Schema(
            description = "Nome do cliente para pedidos do tipo BALCAO (se não houver clienteId)",
            example = "Cliente Balcão"
        )
        String nomeClienteBalcao,
        @Schema(
            description = "Telefone do cliente para pedidos do tipo BALCAO (se não houver clienteId)",
            example = "11987654321"
        )
        String telefoneClienteBalcao,

        @Schema(
            description = "Forma de pagamento escolhida para o pedido",
            allowableValues = {
                "DINHEIRO",
                "CARTAO_CREDITO",
                "CARTAO_DEBITO",
                "PIX"
            },
            example = "PIX"
        )
        FormaPagamento formaPagamento,

        @Schema(
            description = "Valor recebido do cliente (para troco, se aplicável)",
            example = "100.00"
        )
        BigDecimal valorRecebido,

        @Schema(description = "Lista de itens do pedido")
        @NotEmpty(message = "O pedido deve conter pelo menos um item.")
        @Valid
        List<ItemPedidoRequestDTO> itens
) {}