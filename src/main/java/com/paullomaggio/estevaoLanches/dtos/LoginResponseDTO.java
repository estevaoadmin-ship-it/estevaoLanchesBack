package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta contendo o token JWT e os dados do usuário autenticado")
public record LoginResponseDTO(
    @Schema(
        description = "Token JWT para autenticação em requisições futuras",
        example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBlc3RldmFvbGFuY2hlcy5jb20uYnIiLCJyb2xlIjoiQURNSU4iLCJpYXQiOjE2NzgyOTQ0MDAsImV4cCI6MTY3ODI5ODAwMH0.signature"
    )
    String token,

    @Schema(description = "Dados do usuário autenticado")
    UsuarioResponseDTO usuario
) {}