package ru.tuganov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.*;
import ru.tinkoff.piapi.core.MarketDataService;
import ru.tuganov.util.PriceUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Сервис для работы с историей цен и поиском инструментов.
 * Обращается к Tinkoff Invest API для получения данных по акциям.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceHistoryService {

    /** Часовой пояс Московской биржи для корректной конвертации дат */
    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");

    private final MarketDataService marketDataService;

    /**
     * Получает исторические свечи для инструмента за указанный период.
     * Интервал свечей выбирается автоматически в зависимости от длины периода:
     * до 1 дня — минутные, до 7 дней — часовые, до 30 — дневные и т.д.
     */
    public List<HistoricCandle> getCandlesForPeriod(String figi, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(MOSCOW_ZONE).toInstant();
        Instant toInstant = to.atStartOfDay(MOSCOW_ZONE).toInstant();

        CandleInterval candleInterval = determineCandleInterval(fromInstant, toInstant);

        log.debug("Fetching candles for {} from {} to {} with interval {}",
                figi, from, to, candleInterval);

        return marketDataService.getCandlesSync(figi, fromInstant, toInstant, candleInterval);
    }

    /** Определяет подходящий интервал свечей в зависимости от длины запрашиваемого периода. */
    private CandleInterval determineCandleInterval(Instant from, Instant to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 1) return CandleInterval.CANDLE_INTERVAL_1_MIN;
        if (days <= 7) return CandleInterval.CANDLE_INTERVAL_HOUR;
        if (days <= 30) return CandleInterval.CANDLE_INTERVAL_DAY;
        if (days <= 365) return CandleInterval.CANDLE_INTERVAL_WEEK;
        return CandleInterval.CANDLE_INTERVAL_MONTH;
    }
}
