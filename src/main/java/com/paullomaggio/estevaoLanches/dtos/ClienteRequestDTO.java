package com.paullomaggio.estevaoLanches.dtos;

import java.time.LocalDate;

public record ClienteRequestDTO(
        String nome,
        String cpf,
        String email,
        String numero,
        LocalDate dataNascimento
) {}