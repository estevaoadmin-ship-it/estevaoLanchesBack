package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Carrinho;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Schema(description = "Resposta contendo os detalhes de um carrinho de compras")
public record CarrinhoResponseDTO(
        @Schema(
            description = "Identificador único do carrinho",
            format = "uuid",
            example = "a1b2c3d4-e5f6-7890-1234-567890abcdef"
        )
        UUID id,
        @Schema(
            description = "Nome do cliente proprietário do carrinho",
            example = "Maria da Silva"
        )
        String clienteNome,
        @Schema(description = "Lista de itens presentes no carrinho")
        List<ItemCarrinhoResponseDTO> itens
) {
    public CarrinhoResponseDTO(Carrinho carrinho) {
        this(
                carrinho.getId(),
                carrinho.getCliente().getNome(),
                carrinho.getItens().stream().map(ItemCarrinhoResponseDTO::new).collect(Collectors.toList())
        );
    }
}