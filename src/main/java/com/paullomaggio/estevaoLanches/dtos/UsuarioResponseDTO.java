package com.paullomaggio.estevaoLanches.dtos;

import com.paullomaggio.estevaoLanches.entities.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Resposta contendo os dados de um usuário")
public record UsuarioResponseDTO(
        @Schema(
            description = "Identificador único do usuário",
            format = "uuid",
            example = "3f8a2a7d-7fd5-4dc2-b91f-7ef8d648af6b"
        )
        UUID id,
        @Schema(
            description = "Nome completo do usuário",
            example = "Admin Estevao Lanches"
        )
        String nome,
        @Schema(
            description = "Endereço de e-mail do usuário",
            example = "admin@estevaolanches.com.br"
        )
        String email,
        @Schema(
            description = "Papel (role) do usuário",
            example = "ADMIN"
        )
        String role,
        @Schema(
            description = "Status de atividade do usuário",
            example = "true"
        )
        Boolean ativo
) {
    public UsuarioResponseDTO(Usuario usuario) {
        this(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getRole(),
                usuario.isAtivo()
        );
    }
}