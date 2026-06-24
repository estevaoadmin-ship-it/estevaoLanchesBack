package com.paullomaggio.estevaoLanches.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnderecoRequestDTO(
        @Size(max = 50, message = "O rótulo não pode passar de 50 caracteres.")
        String rotulo,

        @NotBlank(message = "O logradouro é obrigatório.")
        @Size(max = 150, message = "O logradouro não pode passar de 150 caracteres.")
        String logradouro,

        @NotBlank(message = "O número é obrigatório.")
        @Size(max = 20, message = "O número não pode passar de 20 caracteres.")
        String numero,

        @Size(max = 100, message = "O complemento não pode passar de 100 caracteres.")
        String complemento,

        @NotBlank(message = "O bairro é obrigatório.")
        @Size(max = 100, message = "O bairro não pode passar de 100 caracteres.")
        String bairro,

        @NotBlank(message = "A cidade é obrigatória.")
        @Size(max = 100, message = "A cidade não pode passar de 100 caracteres.")
        String cidade,

        @NotBlank(message = "O estado (UF) é obrigatório.")
        @Size(min = 2, max = 2, message = "A UF deve conter exatamente 2 caracteres.")
        String uf,

        @NotBlank(message = "O CEP é obrigatório.")
        @Size(max = 9, message = "O CEP não pode passar de 9 caracteres.")
        String cep
) {}