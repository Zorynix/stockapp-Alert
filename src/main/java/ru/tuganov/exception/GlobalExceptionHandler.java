package ru.tuganov.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик исключений для всех REST-контроллеров.
 * Возвращает ответы в формате ProblemDetail (RFC 7807) с понятными сообщениями об ошибках.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Обработка ошибки "ресурс не найден" — возвращает HTTP 404. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Обработка ошибок валидации входных данных — возвращает HTTP 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> "%s: %s".formatted(e.getField(), e.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", errors);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        problem.setTitle("Validation Error");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Обработка нарушений ограничений на параметры запроса (@Validated) — возвращает HTTP 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String errors = ex.getConstraintViolations().stream()
                .map(cv -> cv.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation: {}", errors);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        problem.setTitle("Validation Error");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Обработка некорректных аргументов — возвращает HTTP 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Конфликт при привязке Telegram: оба аккаунта содержат инструменты.
     * Возвращает HTTP 409 с деталями конфликта в теле ответа (ProblemDetail extensions).
     */
    @ExceptionHandler(LinkConflictException.class)
    public ProblemDetail handleLinkConflict(LinkConflictException ex) {
        log.warn("Account link conflict: web={}, telegram={}",
                ex.getWebInstrumentsCount(), ex.getTelegramInstrumentsCount());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Account Link Conflict");
        problem.setProperty("webInstrumentsCount", ex.getWebInstrumentsCount());
        problem.setProperty("telegramInstrumentsCount", ex.getTelegramInstrumentsCount());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Обработка HTTP-ошибок с явно заданным статусом (ResponseStatusException). */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        int statusCode = ex.getStatusCode().value();
        if (statusCode >= 500) {
            log.error("Server error: {}", ex.getReason(), ex);
        } else {
            log.warn("Request error [{}]: {}", statusCode, ex.getReason());
        }
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(statusCode),
                ex.getReason() != null ? ex.getReason() : "Error");
        problem.setTitle(HttpStatus.valueOf(statusCode).getReasonPhrase());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Обработка всех непредвиденных ошибок — возвращает HTTP 500. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
