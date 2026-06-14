package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;
import java.time.LocalDate;
import java.util.List;

public record ClienteRequestDTO(
        @NotBlank(message = "O nome do cliente é obrigatório.")
        @Size(max = 150, message = "O nome não pode passar de 150 caracteres.")
        String nome,

        @CPF(message = "CPF inválido.")
        String cpf,

        // Tiramos o @NotBlank daqui para tornar o e-mail opcional
        @Email(message = "E-mail inválido.")
        @Size(max = 100, message = "O e-mail não pode passar de 100 caracteres.")
        String email,

        // Colocamos o @NotBlank aqui, pois agora o telefone é obrigatório
        @NotBlank(message = "O número de telefone/WhatsApp é obrigatório.")
        @Size(max = 20, message = "O número de telefone não pode passar de 20 caracteres.")
        String numero,

        LocalDate dataNascimento,

        @Valid // Dispara a validação dos endereços enviados na lista
        List<EnderecoRequestDTO> enderecos
) {}