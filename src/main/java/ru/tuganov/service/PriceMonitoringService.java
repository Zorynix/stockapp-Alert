package ru.tuganov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.core.MarketDataService;
import ru.tuganov.dto.PriceAlertEvent;
import ru.tuganov.dto.PriceAlertEvent.AlertType;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.repository.TrackedInstrumentRepository;
import ru.tuganov.util.PriceUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис мониторинга цен в реальном времени.
 *
 * Периодически опрашивает Tinkoff API для получения последних цен
 * по всем отслеживаемым инструментам и генерирует оповещения
 * при выходе цены за установленные пользователем границы.
 *
 * Логика оповещений:
 *   - Цена <= buyPrice  -> BUY-алерт (цена упала ниже порога покупки)
 *   - Цена >= sellPrice -> SELL-алерт (цена выросла выше порога продажи)
 *
 * Алерт отправляется однократно при пересечении границы.
 * Когда цена возвращается в коридор [buyPrice, sellPrice],
 * флаг алерта сбрасывается и при повторном пересечении сработает снова.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class PriceMonitoringService {

    private final TrackedInstrumentRepository trackedInstrumentRepository;
    private final MarketDataService marketDataService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Основной метод проверки цен. Вызывается по расписанию (по умолчанию каждые 5 секунд).
     * Загружает все отслеживаемые инструменты, запрашивает их текущие цены
     * одним batch-запросом и проверяет выход за границы.
     */
    @Scheduled(fixedDelayString = "${monitoring.check-interval-ms:5000}")
    @SchedulerLock(name = "checkPrices", lockAtMostFor = "PT9S", lockAtLeastFor = "PT4S")
    @Transactional
    public void checkPrices() {
        List<TrackedInstrument> allTracked = trackedInstrumentRepository.findAllWithUsers();
        if (allTracked.isEmpty()) {
            return;
        }

        Set<String> figis = allTracked.stream()
                .map(TrackedInstrument::getFigi)
                .collect(Collectors.toSet());

        Map<String, BigDecimal> priceMap = fetchLastPrices(figis);
        if (priceMap.isEmpty()) {
            log.warn("No prices returned from API for {} instruments", figis.size());
            return;
        }

        List<TrackedInstrument> toSave = new ArrayList<>();

        for (TrackedInstrument tracked : allTracked) {
            BigDecimal currentPrice = priceMap.get(tracked.getFigi());
            if (currentPrice == null) {
                log.debug("No price available for figi={}", tracked.getFigi());
                continue;
            }

            if (evaluateBoundaries(tracked, currentPrice)) {
                toSave.add(tracked);
            }
        }

        if (!toSave.isEmpty()) {
            trackedInstrumentRepository.saveAll(toSave);
            log.debug("Updated alert state for {} tracked instruments", toSave.size());
        }
    }

    /**
     * Получает последние цены для набора FIGI одним запросом к API.
     * В случае ошибки API возвращает пустую map и логирует ошибку.
     */
    private Map<String, BigDecimal> fetchLastPrices(Set<String> figis) {
        try {
            return marketDataService.getLastPricesSync(new ArrayList<>(figis))
                    .stream()
                    .filter(lp -> !lp.getFigi().isEmpty())
                    .collect(Collectors.toMap(
                            LastPrice::getFigi,
                            lp -> PriceUtils.toBigDecimal(lp.getPrice()),
                            (a, b) -> a
                    ));
        } catch (Exception e) {
            log.error("Failed to fetch last prices from Tinkoff API", e);
            return Map.of();
        }
    }

    /**
     * Проверяет, вышла ли текущая цена за установленные пользователем границы,
     * и при необходимости публикует алерт.
     *
     * @return true если состояние алертов изменилось и инструмент нужно сохранить в БД
     */
    private boolean evaluateBoundaries(TrackedInstrument tracked, BigDecimal currentPrice) {
        boolean changed = false;

        // Проверка нижней границы (BUY): цена упала ниже или равна порогу покупки
        if (currentPrice.compareTo(tracked.getBuyPrice()) <= 0) {
            if (!tracked.isBuyAlertSent()) {
                publishAlert(tracked, currentPrice, tracked.getBuyPrice(), AlertType.BUY);
                tracked.setBuyAlertSent(true);
                changed = true;
            }
        } else if (tracked.isBuyAlertSent()) {
            // Цена вернулась выше порога покупки — сбрасываем флаг алерта
            tracked.setBuyAlertSent(false);
            changed = true;
        }

        // Проверка верхней границы (SELL): цена выросла выше или равна порогу продажи
        if (currentPrice.compareTo(tracked.getSellPrice()) >= 0) {
            if (!tracked.isSellAlertSent()) {
                publishAlert(tracked, currentPrice, tracked.getSellPrice(), AlertType.SELL);
                tracked.setSellAlertSent(true);
                changed = true;
            }
        } else if (tracked.isSellAlertSent()) {
            // Цена вернулась ниже порога продажи — сбрасываем флаг алерта
            tracked.setSellAlertSent(false);
            changed = true;
        }

        return changed;
    }

    /**
     * Формирует событие алерта и публикует его через Spring Events.
     * Обработкой события занимается AlertNotificationService.
     */
    private void publishAlert(TrackedInstrument tracked, BigDecimal currentPrice,
                              BigDecimal threshold, AlertType alertType) {
        var user = tracked.getUser();
        var alert = new PriceAlertEvent(
                tracked.getInstrumentName(),
                tracked.getFigi(),
                currentPrice,
                threshold,
                alertType,
                user.getId(),
                user.getTelegramId(),
                user.getChatId()
        );

        log.info("Price alert [{}]: {} ({}) — price: {}, threshold: {}, user: {}",
                alertType, tracked.getInstrumentName(), tracked.getFigi(),
                currentPrice, threshold, user.getId());

        eventPublisher.publishEvent(alert);
    }
}
