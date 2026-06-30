package com.paullomaggio.estevaoLanches.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para registro de uma nova conta de cliente para o aplicativo de delivery")
public record RegistroDeliveryRequestDTO(
        @Schema(
            description = "Nome completo do cliente",
            example = "Maria da Silva",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 150)
        String nome,

        @Schema(
            description = "Endereço de e-mail do cliente",
            example = "maria.silva@email.com",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        String email,

        @Schema(
            description = "Número de telefone do cliente",
            example = "5511987654321",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "O número de telefone é obrigatório.")
        String telefone,

        @Schema(
            description = "Senha para a conta do cliente",
            example = "senhaSegura123",
            requiredMode = Schema.RequiredMode.REQUIRED,
            format = "password"
        )
        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres.")
        String senha
) {}