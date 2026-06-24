package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Cliente;
import com.paullomaggio.estevaoLanches.enums.StatusCliente;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record ClienteResponseDTO(
        UUID id,
        String nome,
        String cpf,
        String email,
        String numero,
        LocalDate dataNascimento,
        StatusCliente status,
        List<EnderecoResponseDTO> enderecos
) {
    public ClienteResponseDTO(Cliente cliente) {
        this(
                cliente.getId(),
                cliente.getNome(),
                cliente.getCpf(),
                cliente.getEmail(),
                cliente.getNumero(),
                cliente.getDataNascimento(),
                cliente.getStatus(),
                cliente.getEnderecos().stream().map(EnderecoResponseDTO::new).collect(Collectors.toList())
        );
    }
}