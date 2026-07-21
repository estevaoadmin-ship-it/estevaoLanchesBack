package com.paullomaggio.estevaoLanches.dtos;

import java.util.List;
import java.util.UUID;

/**
 * 📝 ESTRUTURA MESTRA DE RECEBIMENTO DO SALÃO:
 * Desserializa rigidamente o fluxo da sacola emitido pelo Angular sem perda de pacotes.
 */
public record PedidoMobileRequestDTO(
        UUID comandaId,
        Integer numeroMesa,
        Integer numeroConta,
        ClientePayloadDTO cliente, // O Angular agora despacha corretamente a chave "cliente" aninhada
        List<ItemPedidoPayloadDTO> itens
) {
        public record ClientePayloadDTO(
                String nome, // O Angular enviará a chave 'nome', batendo com o Jackson do Spring
                String telefone
        ) {}

        public record ItemPedidoPayloadDTO(
                UUID produtoId,
                String nome,
                Integer quantidade,
                Double precoCalculado,
                String observacao,
                List<UUID> adicionaisIds, // Recebe apenas a lista de UUIDs dos adicionais atrelados
                List<ItemComboCustomizacaoRequestDTO> itensCombo
        ) {
                // Construtor retrocompatível
                public ItemPedidoPayloadDTO(
                        UUID produtoId,
                        String nome,
                        Integer quantidade,
                        Double precoCalculado,
                        String observacao,
                        List<UUID> adicionaisIds
                ) {
                        this(
                                produtoId,
                                nome,
                                quantidade,
                                precoCalculado,
                                observacao,
                                adicionaisIds,
                                null // itensCombo é nulo para retrocompatibilidade
                        );
                }
        }
}