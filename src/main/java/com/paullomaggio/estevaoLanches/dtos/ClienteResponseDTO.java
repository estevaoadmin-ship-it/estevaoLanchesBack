package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Cliente;
import java.util.UUID;

public record ClienteResponseDTO(
        UUID id,
        String nome,
        String email,
        String numero
) {
    public ClienteResponseDTO(Cliente cliente) {
        this(cliente.getId(), cliente.getNome(), cliente.getEmail(), cliente.getNumero());
    }
}