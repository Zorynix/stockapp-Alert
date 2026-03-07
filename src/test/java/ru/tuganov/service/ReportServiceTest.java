package ru.tuganov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.Quotation;
import com.google.protobuf.Timestamp;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private PriceHistoryService priceHistoryService;
    @InjectMocks private ReportService service;

    @Test
    void generateStockReport_markdownFormat_containsHeader() {
        when(priceHistoryService.getCandlesForPeriod(eq("BBG000B9XRY4"), any(), any()))
                .thenReturn(List.of(candle(100, 110, 90, 105)));

        byte[] result = service.generateStockReport("BBG000B9XRY4", "Apple", "1m", "md");

        String md = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(md).contains("# StockApp");
        assertThat(md).contains("Apple");
        assertThat(md).contains("BBG000B9XRY4");
    }

    @Test
    void generateStockReport_markdownFormat_emptyCandles_containsNoDataMessage() {
        when(priceHistoryService.getCandlesForPeriod(any(), any(), any())).thenReturn(List.of());

        byte[] result = service.generateStockReport("BBG000B9XRY4", "Apple", "1m", "md");

        String md = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(md).contains("Нет данных");
    }

    @Test
    void generateStockReport_pdfFormat_returnsPdfBytes() {
        when(priceHistoryService.getCandlesForPeriod(any(), any(), any()))
                .thenReturn(List.of(candle(100, 110, 90, 105)));

        byte[] result = service.generateStockReport("BBG000B9XRY4", "Apple", "1m", "pdf");

        assertThat(result).isNotEmpty();
        // PDF magic bytes: %PDF
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateStockReport_pdfFormat_emptyCandles_returnsPdfBytes() {
        when(priceHistoryService.getCandlesForPeriod(any(), any(), any())).thenReturn(List.of());

        byte[] result = service.generateStockReport("BBG000B9XRY4", "Apple", "1m", "pdf");

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generateStockReport_invalidPeriod_throwsException() {
        assertThatThrownBy(() -> service.generateStockReport("BBG000B9XRY4", "Apple", "99y", "md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid period");
    }

    // helper: creates a HistoricCandle with given OHLC prices (units only, nano=0)
    private HistoricCandle candle(long open, long high, long low, long close) {
        long nowEpoch = LocalDate.now().atStartOfDay(java.time.ZoneId.of("Europe/Moscow")).toEpochSecond();
        return HistoricCandle.newBuilder()
                .setOpen(Quotation.newBuilder().setUnits(open).build())
                .setHigh(Quotation.newBuilder().setUnits(high).build())
                .setLow(Quotation.newBuilder().setUnits(low).build())
                .setClose(Quotation.newBuilder().setUnits(close).build())
                .setVolume(1000)
                .setTime(Timestamp.newBuilder().setSeconds(nowEpoch).build())
                .build();
    }
}
