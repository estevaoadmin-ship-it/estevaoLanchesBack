package com.paullomaggio.estevaoLanches.exceptions;

/**
 * Exceção de aplicação para representar conflito de estado:
 * um recurso que já existe com dados exclusivos (ex.: e-mail duplicado).
 * Mapeada via GlobalExceptionHandler para HTTP 409 CONFLICT.
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}