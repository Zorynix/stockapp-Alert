package ru.tuganov.exception;

import lombok.Getter;

/**
 * Исключение при попытке привязать Telegram к аккаунту, когда оба аккаунта
 * содержат отслеживаемые инструменты. Требует явного выбора стратегии разрешения.
 */
@Getter
public class LinkConflictException extends RuntimeException {

    private final int webInstrumentsCount;
    private final int telegramInstrumentsCount;

    public LinkConflictException(int webInstrumentsCount, int telegramInstrumentsCount) {
        super("Оба аккаунта содержат отслеживаемые инструменты — выберите стратегию объединения");
        this.webInstrumentsCount = webInstrumentsCount;
        this.telegramInstrumentsCount = telegramInstrumentsCount;
    }
}
