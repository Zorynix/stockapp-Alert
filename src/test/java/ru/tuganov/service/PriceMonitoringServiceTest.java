package ru.tuganov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.core.MarketDataService;
import ru.tuganov.dto.PriceAlertEvent;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.repository.TrackedInstrumentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceMonitoringServiceTest {

    @Mock private TrackedInstrumentRepository trackedInstrumentRepository;
    @Mock private MarketDataService marketDataService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private PriceMonitoringService service;

    @Test
    void checkPrices_emptyList_doesNothing() {
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of());

        service.checkPrices();

        verifyNoInteractions(marketDataService, eventPublisher);
    }

    @Test
    void checkPrices_noPricesReturned_doesNotPublish() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti = ti(user, "BBG000B9XRY4", "90", "110");
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of(ti));
        when(marketDataService.getLastPricesSync(anyList())).thenReturn(List.of());

        service.checkPrices();

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkPrices_priceAtOrBelowBuy_publishesBuyAlert() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti = ti(user, "BBG000B9XRY4", "100", "200");
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of(ti));
        // price = 95, which is <= buyPrice 100
        when(marketDataService.getLastPricesSync(anyList()))
                .thenReturn(List.of(lastPrice("BBG000B9XRY4", 95, 0)));

        service.checkPrices();

        ArgumentCaptor<PriceAlertEvent> captor = ArgumentCaptor.forClass(PriceAlertEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().alertType()).isEqualTo(PriceAlertEvent.AlertType.BUY);
        assertThat(ti.isBuyAlertSent()).isTrue();
    }

    @Test
    void checkPrices_buyAlertAlreadySent_doesNotPublishAgain() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti = ti(user, "BBG000B9XRY4", "100", "200");
        ti.setBuyAlertSent(true); // already sent
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of(ti));
        when(marketDataService.getLastPricesSync(anyList()))
                .thenReturn(List.of(lastPrice("BBG000B9XRY4", 95, 0)));

        service.checkPrices();

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkPrices_priceReturnsAboveBuy_resetsBuyAlertFlag() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti = ti(user, "BBG000B9XRY4", "100", "200");
        ti.setBuyAlertSent(true); // was triggered
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of(ti));
        // price = 150, which is > buyPrice 100 → reset
        when(marketDataService.getLastPricesSync(anyList()))
                .thenReturn(List.of(lastPrice("BBG000B9XRY4", 150, 0)));

        service.checkPrices();

        assertThat(ti.isBuyAlertSent()).isFalse();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkPrices_priceAtOrAboveSell_publishesSellAlert() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti = ti(user, "BBG000B9XRY4", "100", "200");
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of(ti));
        // price = 210, which is >= sellPrice 200
        when(marketDataService.getLastPricesSync(anyList()))
                .thenReturn(List.of(lastPrice("BBG000B9XRY4", 210, 0)));

        service.checkPrices();

        ArgumentCaptor<PriceAlertEvent> captor = ArgumentCaptor.forClass(PriceAlertEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().alertType()).isEqualTo(PriceAlertEvent.AlertType.SELL);
        assertThat(ti.isSellAlertSent()).isTrue();
    }

    @Test
    void checkPrices_sellAlertAlreadySent_doesNotPublishAgain() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti = ti(user, "BBG000B9XRY4", "100", "200");
        ti.setSellAlertSent(true);
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of(ti));
        when(marketDataService.getLastPricesSync(anyList()))
                .thenReturn(List.of(lastPrice("BBG000B9XRY4", 210, 0)));

        service.checkPrices();

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkPrices_priceReturnsBelowSell_resetsSellAlertFlag() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti = ti(user, "BBG000B9XRY4", "100", "200");
        ti.setSellAlertSent(true);
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of(ti));
        // price = 150, back in range
        when(marketDataService.getLastPricesSync(anyList()))
                .thenReturn(List.of(lastPrice("BBG000B9XRY4", 150, 0)));

        service.checkPrices();

        assertThat(ti.isSellAlertSent()).isFalse();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkPrices_apiError_doesNotCrash() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti = ti(user, "BBG000B9XRY4", "100", "200");
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of(ti));
        when(marketDataService.getLastPricesSync(anyList()))
                .thenThrow(new RuntimeException("API down"));

        assertThatNoException().isThrownBy(() -> service.checkPrices());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void checkPrices_changedInstruments_savedInBatch() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti1 = ti(user, "BBG000B9XRY4", "100", "200");
        TrackedInstrument ti2 = ti(user, "BBG000B9XRY5", "50", "80");
        when(trackedInstrumentRepository.findAllWithUsers()).thenReturn(List.of(ti1, ti2));
        when(marketDataService.getLastPricesSync(anyList()))
                .thenReturn(List.of(
                        lastPrice("BBG000B9XRY4", 90, 0), // triggers BUY
                        lastPrice("BBG000B9XRY5", 85, 0)  // triggers SELL
                ));

        service.checkPrices();

        verify(trackedInstrumentRepository).saveAll(anyList());
    }

    // helpers

    private AppUser user(UUID id) {
        AppUser u = new AppUser();
        u.setId(id);
        return u;
    }

    private TrackedInstrument ti(AppUser user, String figi, String buy, String sell) {
        TrackedInstrument ti = new TrackedInstrument();
        ti.setUser(user);
        ti.setFigi(figi);
        ti.setInstrumentName("Test");
        ti.setBuyPrice(new BigDecimal(buy));
        ti.setSellPrice(new BigDecimal(sell));
        return ti;
    }

    private LastPrice lastPrice(String figi, long units, int nano) {
        return LastPrice.newBuilder()
                .setFigi(figi)
                .setPrice(Quotation.newBuilder().setUnits(units).setNano(nano).build())
                .build();
    }
}
