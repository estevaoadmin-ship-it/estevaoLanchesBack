package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Carrinho;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record CarrinhoResponseDTO(
        UUID id,
        String clienteNome,
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