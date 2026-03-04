package ru.tuganov.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String instrumentName,
        String figi,
        String alertType,
        BigDecimal currentPrice,
        BigDecimal threshold,
        String message,
        boolean sentToTelegram,
        Instant createdAt
) {
}
