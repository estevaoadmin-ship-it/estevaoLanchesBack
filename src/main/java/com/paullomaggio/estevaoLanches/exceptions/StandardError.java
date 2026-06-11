package com.paullomaggio.estevaoLanches.exceptions;

import java.time.Instant;
import java.util.List;

public record StandardError(
        Instant timestamp,
        Integer status,
        String error,
        String message,
        String path,
        List<String> fieldErrors // Lista útil para guardar erros de validação (@NotBlank, @NotNull)
) {
    // Construtor simplificado para erros que não são de validação de formulário
    public StandardError(Instant timestamp, Integer status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
}