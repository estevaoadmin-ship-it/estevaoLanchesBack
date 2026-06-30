package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciais utilizadas para autenticação do usuário")
public record LoginRequestDTO(
    @Schema(
        description = "E-mail do usuário",
        example = "admin@estevaolanches.com.br",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String email,

    @Schema(
        description = "Senha do usuário",
        example = "123456",
        requiredMode = Schema.RequiredMode.REQUIRED,
        format = "password"
    )
    String senha
) {}