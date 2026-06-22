package com.paullomaggio.estevaoLanches.dtos;

public record ClienteLoginResponseDTO(
        String token,
        String nome,
        String email,
        String role
) {}