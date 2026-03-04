package ru.tuganov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.tuganov.dto.PriceAlertEvent;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.Notification;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.NotificationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Сервис обработки уведомлений о ценовых алертах.
 *
 * Слушает события PriceAlertEvent и:
 * - Отправляет Telegram-уведомление (если пользователь привязал Telegram)
 * - Отправляет email-уведомление (если email подтверждён и уведомления включены)
 * - Сохраняет историю уведомлений в БД
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertNotificationService {

    private final TelegramBotService telegramBotService;
    private final EmailService emailService;
    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePriceAlert(PriceAlertEvent alert) {
        switch (alert.alertType()) {
            case BUY -> log.warn(
                    "📉 BUY ALERT: {} ({}) dropped to {} (threshold: {}). User: {}, Chat: {}",
                    alert.instrumentName(), alert.figi(),
                    alert.currentPrice(), alert.threshold(),
                    alert.appUserId(), alert.chatId()
            );
            case SELL -> log.warn(
                    "📈 SELL ALERT: {} ({}) rose to {} (threshold: {}). User: {}, Chat: {}",
                    alert.instrumentName(), alert.figi(),
                    alert.currentPrice(), alert.threshold(),
                    alert.appUserId(), alert.chatId()
            );
        }

        // Загружаем пользователя для проверки настроек уведомлений
        AppUser appUser = appUserRepository.findById(alert.appUserId()).orElse(null);
        if (appUser == null) {
            log.error("AppUser not found for alert: {}", alert.appUserId());
            return;
        }

        // Telegram-уведомление (если привязан)
        boolean sentToTelegram = false;
        if (alert.chatId() != null) {
            sentToTelegram = telegramBotService.sendPriceAlert(alert);
        }

        // Email-уведомление (лучшие усилия — ошибка не прерывает сохранение в БД)
        if (appUser.isEmailConfirmed() && appUser.isEmailNotificationsEnabled()
                && appUser.getEmail() != null) {
            try {
                emailService.sendPriceAlert(
                        appUser.getEmail(),
                        alert.instrumentName(),
                        alert.figi(),
                        alert.alertType().name(),
                        formatPrice(alert.currentPrice()),
                        formatPrice(alert.threshold())
                );
                log.info("Email alert sent to {}", appUser.getEmail());
            } catch (Exception e) {
                log.error("Failed to send email alert to {}: {}", appUser.getEmail(), e.getMessage());
            }
        }

        // Сохраняем запись в БД
        Notification notification = new Notification();
        notification.setAppUser(appUser);
        notification.setUserId(alert.telegramUserId());
        notification.setChatId(alert.chatId());
        notification.setInstrumentName(alert.instrumentName());
        notification.setFigi(alert.figi());
        notification.setAlertType(Notification.AlertType.valueOf(alert.alertType().name()));
        notification.setCurrentPrice(alert.currentPrice());
        notification.setThreshold(alert.threshold());
        notification.setMessage(formatMessage(alert));
        notification.setSentToTelegram(sentToTelegram);

        notificationRepository.save(notification);
        log.info("Notification saved for user {} (telegram: {}, email: {})",
                alert.appUserId(), sentToTelegram,
                appUser.isEmailConfirmed() && appUser.isEmailNotificationsEnabled());
    }

    private String formatMessage(PriceAlertEvent alert) {
        String action = alert.alertType() == PriceAlertEvent.AlertType.BUY ? "Покупка" : "Продажа";
        String direction = alert.alertType() == PriceAlertEvent.AlertType.BUY
                ? "упала ниже порога" : "поднялась выше порога";
        return "%s: %s (%s) — цена %s ₽ %s %s ₽".formatted(
                action, alert.instrumentName(), alert.figi(),
                formatPrice(alert.currentPrice()), direction,
                formatPrice(alert.threshold())
        );
    }

    private String formatPrice(BigDecimal price) {
        return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
