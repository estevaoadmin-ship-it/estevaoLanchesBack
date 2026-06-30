package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta de autenticação contendo o token JWT e informações básicas do usuário")
public record AuthResponseDTO(
    @Schema(
        description = "Token JWT para autenticação em requisições futuras",
        example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBlc3RldmFvbGFuY2hlcy5jb20uYnIiLCJyb2xlIjoiQURNSU4iLCJpYXQiOjE2NzgyOTQ0MDAsImV4cCI6MTY3ODI5ODAwMH0.signature"
    )
    String token,

    @Schema(
        description = "Nome do usuário autenticado",
        example = "Admin Estevao Lanches"
    )
    String nome,

    @Schema(
        description = "Papel (role) do usuário autenticado",
        example = "ADMIN"
    )
    String role
) {}