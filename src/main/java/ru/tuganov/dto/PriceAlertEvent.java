package ru.tuganov.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Событие ценового алерта.
 * Генерируется когда цена отслеживаемого инструмента выходит за установленные границы.
 */
public record PriceAlertEvent(
        String instrumentName,
        String figi,
        BigDecimal currentPrice,
        BigDecimal threshold,
        AlertType alertType,
        /** UUID пользователя в системе (AppUser.id). */
        UUID appUserId,
        /** Telegram user ID. Null для email-only пользователей. */
        Long telegramUserId,
        /** Telegram chat ID для отправки уведомлений. Null для email-only пользователей. */
        Long chatId
) {

    public enum AlertType {
        BUY,
        SELL
    }
}
