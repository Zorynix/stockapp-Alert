package ru.tuganov.exception;

/**
 * Исключение, которое бросается когда запрашиваемый ресурс не найден в БД.
 * Обрабатывается в GlobalExceptionHandler и возвращает HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found with id: %s".formatted(resource, id));
    }
}
