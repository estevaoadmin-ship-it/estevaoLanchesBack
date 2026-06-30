package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Pedido;
import com.paullomaggio.estevaoLanches.enums.StatusPedido;
import com.paullomaggio.estevaoLanches.enums.StatusFinanceiro;
import com.paullomaggio.estevaoLanches.enums.TipoPedido;
import com.paullomaggio.estevaoLanches.enums.FormaPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Schema(description = "Resposta contendo os detalhes de um pedido")
public record PedidoResponseDTO(
        @Schema(
            description = "Identificador único do pedido",
            format = "uuid",
            example = "3f8a2a7d-7fd5-4dc2-b91f-7ef8d648af6b"
        )
        UUID id,
        @Schema(
            description = "Número sequencial do pedido",
            example = "PED-20230630-0001"
        )
        String numeroPedido,
        @Schema(
            description = "Nome do cliente que realizou o pedido",
            example = "Maria da Silva"
        )
        String clienteNome,
        @Schema(
            description = "Data e hora de criação do pedido",
            example = "2026-06-30T19:45:10"
        )
        LocalDateTime dataHora,
        @Schema(
            description = "Status atual do pedido",
            allowableValues = {
                "PENDENTE",
                "EM_PREPARO",
                "PRONTO",
                "FINALIZADO",
                "CANCELADO"
            },
            example = "EM_PREPARO"
        )
        StatusPedido status,
        @Schema(
            description = "Status financeiro do pedido",
            allowableValues = {
                "PENDENTE",
                "PAGO",
                "CANCELADO"
            },
            example = "PAGO"
        )
        StatusFinanceiro statusFinanceiro,
        @Schema(
            description = "Forma de pagamento utilizada no pedido",
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
            description = "Tipo do pedido",
            allowableValues = {"MESA", "BALCAO", "DELIVERY"},
            example = "DELIVERY"
        )
        TipoPedido tipo,
        @Schema(
            description = "Valor total do pedido",
            example = "89.90"
        )
        BigDecimal total,
        @Schema(
            description = "Endereço de entrega (se for um pedido de delivery)",
            example = "Rua Exemplo, 123, Bairro, Cidade - SP"
        )
        String enderecoEntrega,
        @Schema(
            description = "Número da mesa (se for um pedido de mesa)",
            example = "5"
        )
        Integer numeroMesa,
        @Schema(
            description = "Observação geral do pedido",
            example = "Entregar com guardanapos extras."
        )
        String observacaoGeral,
        @Schema(description = "Lista de itens do pedido")
        List<ItemPedidoResponseDTO> itens
) {
    public PedidoResponseDTO(Pedido pedido) {
        this(
                pedido.getId(),
                pedido.getNumeroPedido(),
                pedido.getCliente() != null ? pedido.getCliente().getNome() : pedido.getNomeClienteBalcao(),
                pedido.getDataHora(),
                pedido.getStatus(),
                pedido.getStatusFinanceiro(),
                pedido.getFormaPagamento(),
                pedido.getTipo(),
                pedido.getTotal(),
                pedido.getEnderecoEntrega(),
                pedido.getNumeroMesa(),
                pedido.getObservacaoGeral(),
                pedido.getItens() != null ? pedido.getItens().stream().map(ItemPedidoResponseDTO::new).collect(Collectors.toList()) : List.of()
        );
    }
}