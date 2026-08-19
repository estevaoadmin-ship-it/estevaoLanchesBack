package com.paullomaggio.estevaoLanches.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<StandardError> entityNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        StandardError err = new StandardError(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "Recurso não encontrado",
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<StandardError> businessRule(BusinessRuleException e, HttpServletRequest request) {
        StandardError err = new StandardError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Violação de Regra de Negócio",
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<StandardError> duplicateResource(DuplicateResourceException e, HttpServletRequest request) {
        StandardError err = new StandardError(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "Conflito de Recurso Existente",
                e.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
    }

    // 🎯 FIX CRÍTICO: Analisa dinamicamente a causa raiz para não mascarar comandos de INSERT/UPDATE
    @ExceptionHandler({DatabaseIntegrityException.class, DataIntegrityViolationException.class})
    public ResponseEntity<StandardError> databaseIntegrity(Exception e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;
        String mensagemAmigavel = "Erro de integridade de dados ao processar a operação no banco.";

        // Extrai a mensagem nativa do driver JDBC/PostgreSQL
        Throwable causaRaiz = e.getCause();
        while (causaRaiz != null && causaRaiz.getCause() != null) {
            causaRaiz = causaRaiz.getCause();
        }

        String msgOriginal = (causaRaiz != null) ? causaRaiz.getMessage().toLowerCase() : e.getMessage().toLowerCase();

        if (msgOriginal.contains("violates not-null constraint")) {
            String coluna = "desconhecida";
            try {
                int inicio = msgOriginal.indexOf("\"") + 1;
                int fim = msgOriginal.indexOf("\"", inicio);
                coluna = msgOriginal.substring(inicio, fim);
            } catch (Exception ex) { /* Fallback seguro */ }
            mensagemAmigavel = "O campo obrigatório '" + coluna + "' não foi informado ou está nulo.";
        } else if (msgOriginal.contains("duplicate key value violates unique constraint")) {
            mensagemAmigavel = "Operação negada. Já existe um registro ativo com esses dados exclusivos informados.";
        } else if (msgOriginal.contains("violates foreign key constraint") || msgOriginal.contains("is still referenced from table")) {
            mensagemAmigavel = "Não é possível excluir ou alterar este registro pois ele possui histórico ou vínculos ativos no sistema.";
        }

        StandardError err = new StandardError(
                Instant.now(),
                status.value(),
                "Conflito de Integridade no Banco de Dados",
                mensagemAmigavel,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(err);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardError> validationError(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<String> errosDeValidacao = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        StandardError err = new StandardError(
                Instant.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Erro de Validação dos Dados",
                "Um ou mais campos estão inválidos. Verifique a lista de erros.",
                request.getRequestURI(),
                errosDeValidacao
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(err);
    }
}