package ru.tuganov.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ответ с данными отслеживаемого инструмента.
 * Содержит всю информацию: ID, FIGI, границы, статусы алертов и дату создания.
 */
public record TrackedInstrumentResponse(
        UUID id,
        String figi,
        String instrumentName,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        boolean buyAlertSent,
        boolean sellAlertSent,
        Instant createdAt,
        /** AppUser UUID владельца. */
        UUID appUserId
) {
}
