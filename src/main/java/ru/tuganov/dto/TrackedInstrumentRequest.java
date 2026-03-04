package ru.tuganov.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Запрос на создание/обновление отслеживаемого инструмента.
 * Содержит FIGI инструмента, название, ценовые границы и данные пользователя.
 */
public record TrackedInstrumentRequest(

        @NotBlank(message = "FIGI is required")
        String figi,

        @NotBlank(message = "Instrument name is required")
        String instrumentName,

        @NotNull(message = "Buy price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Buy price must be positive")
        BigDecimal buyPrice,

        @NotNull(message = "Sell price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Sell price must be positive")
        BigDecimal sellPrice,

        /** Telegram user ID. Опционален при JWT-аутентификации. */
        Long userId,

        /** Telegram chat ID. Опционален при JWT-аутентификации. */
        Long chatId
) {
}
