package ru.tuganov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.core.MarketDataService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock private MarketDataService marketDataService;
    @InjectMocks private PriceHistoryService service;

    @Test
    void getCandlesForPeriod_oneDay_uses1MinInterval() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 1); // same day (0 days)
        when(marketDataService.getCandlesSync(eq("BBG000B9XRY4"), any(Instant.class), any(Instant.class),
                eq(CandleInterval.CANDLE_INTERVAL_1_MIN)))
                .thenReturn(List.of());

        List<HistoricCandle> result = service.getCandlesForPeriod("BBG000B9XRY4", from, to);

        assertThat(result).isEmpty();
        verify(marketDataService).getCandlesSync(eq("BBG000B9XRY4"), any(), any(),
                eq(CandleInterval.CANDLE_INTERVAL_1_MIN));
    }

    @Test
    void getCandlesForPeriod_7days_usesHourInterval() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 7); // 6 days
        when(marketDataService.getCandlesSync(any(), any(Instant.class), any(Instant.class),
                eq(CandleInterval.CANDLE_INTERVAL_HOUR)))
                .thenReturn(List.of());

        service.getCandlesForPeriod("BBG000B9XRY4", from, to);

        verify(marketDataService).getCandlesSync(any(), any(), any(),
                eq(CandleInterval.CANDLE_INTERVAL_HOUR));
    }

    @Test
    void getCandlesForPeriod_30days_usesDayInterval() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 20); // 19 days
        when(marketDataService.getCandlesSync(any(), any(Instant.class), any(Instant.class),
                eq(CandleInterval.CANDLE_INTERVAL_DAY)))
                .thenReturn(List.of());

        service.getCandlesForPeriod("BBG000B9XRY4", from, to);

        verify(marketDataService).getCandlesSync(any(), any(), any(),
                eq(CandleInterval.CANDLE_INTERVAL_DAY));
    }

    @Test
    void getCandlesForPeriod_365days_usesWeekInterval() {
        LocalDate from = LocalDate.of(2023, 1, 1);
        LocalDate to = LocalDate.of(2023, 12, 1); // ~334 days
        when(marketDataService.getCandlesSync(any(), any(Instant.class), any(Instant.class),
                eq(CandleInterval.CANDLE_INTERVAL_WEEK)))
                .thenReturn(List.of());

        service.getCandlesForPeriod("BBG000B9XRY4", from, to);

        verify(marketDataService).getCandlesSync(any(), any(), any(),
                eq(CandleInterval.CANDLE_INTERVAL_WEEK));
    }

    @Test
    void getCandlesForPeriod_moreThan365days_usesMonthInterval() {
        LocalDate from = LocalDate.of(2022, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 1); // 2 years
        when(marketDataService.getCandlesSync(any(), any(Instant.class), any(Instant.class),
                eq(CandleInterval.CANDLE_INTERVAL_MONTH)))
                .thenReturn(List.of());

        service.getCandlesForPeriod("BBG000B9XRY4", from, to);

        verify(marketDataService).getCandlesSync(any(), any(), any(),
                eq(CandleInterval.CANDLE_INTERVAL_MONTH));
    }

    @Test
    void getCandlesForPeriod_returnsResultFromApi() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 1);
        HistoricCandle candle = HistoricCandle.newBuilder().build();
        when(marketDataService.getCandlesSync(any(), any(), any(), any()))
                .thenReturn(List.of(candle));

        List<HistoricCandle> result = service.getCandlesForPeriod("BBG000B9XRY4", from, to);

        assertThat(result).hasSize(1);
    }
}
