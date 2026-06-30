package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciais utilizadas para autenticação via Google")
public record GoogleLoginRequestDTO(
    @Schema(
        description = "Token de ID do Google para autenticação",
        example = "eyJhbGciOiJIUzI1NiJ9...",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String idToken
) {}