package ru.tuganov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.tuganov.dto.PriceAlertEvent;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.Notification;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.NotificationRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertNotificationServiceTest {

    @Mock private TelegramBotService telegramBotService;
    @Mock private EmailService emailService;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AppUserRepository appUserRepository;
    @InjectMocks private AlertNotificationService service;

    @Test
    void handlePriceAlert_telegramUser_sendsTelegramAndSavesNotification() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, null, null, 123L);
        PriceAlertEvent alert = buyAlert(userId, 123L, 123L);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(telegramBotService.sendPriceAlert(alert)).thenReturn(true);

        service.handlePriceAlert(alert);

        verify(telegramBotService).sendPriceAlert(alert);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void handlePriceAlert_emailUser_sendsEmailAndSavesNotification() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, "user@test.com", "hashed", null);
        user.setEmailConfirmed(true);
        user.setEmailNotificationsEnabled(true);
        PriceAlertEvent alert = buyAlert(userId, null, null);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

        service.handlePriceAlert(alert);

        verify(emailService).sendPriceAlert(eq("user@test.com"), any(), any(), any(), any(), any());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void handlePriceAlert_emailNotEnabled_doesNotSendEmail() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, "user@test.com", "hashed", null);
        user.setEmailConfirmed(true);
        user.setEmailNotificationsEnabled(false); // disabled
        PriceAlertEvent alert = buyAlert(userId, null, null);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

        service.handlePriceAlert(alert);

        verifyNoInteractions(emailService);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void handlePriceAlert_emailNotConfirmed_doesNotSendEmail() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, "user@test.com", "hashed", null);
        user.setEmailConfirmed(false); // not confirmed
        PriceAlertEvent alert = buyAlert(userId, null, null);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

        service.handlePriceAlert(alert);

        verifyNoInteractions(emailService);
    }

    @Test
    void handlePriceAlert_userNotFound_doesNotSave() {
        UUID userId = UUID.randomUUID();
        when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

        service.handlePriceAlert(buyAlert(userId, null, null));

        verifyNoInteractions(notificationRepository, telegramBotService, emailService);
    }

    @Test
    void handlePriceAlert_bothChannels_sendsBothAndSaves() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, "user@test.com", "hashed", 999L);
        user.setEmailConfirmed(true);
        user.setEmailNotificationsEnabled(true);
        PriceAlertEvent alert = buyAlert(userId, 999L, 999L);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(telegramBotService.sendPriceAlert(alert)).thenReturn(true);

        service.handlePriceAlert(alert);

        verify(telegramBotService).sendPriceAlert(alert);
        verify(emailService).sendPriceAlert(eq("user@test.com"), any(), any(), any(), any(), any());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void handlePriceAlert_emailFails_stillSavesNotification() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, "user@test.com", "hashed", null);
        user.setEmailConfirmed(true);
        user.setEmailNotificationsEnabled(true);
        PriceAlertEvent alert = buyAlert(userId, null, null);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("SMTP error"))
                .when(emailService).sendPriceAlert(any(), any(), any(), any(), any(), any());

        assertThatNoException().isThrownBy(() -> service.handlePriceAlert(alert));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void handlePriceAlert_sellAlert_savesWithSellType() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, null, null, 123L);
        PriceAlertEvent alert = new PriceAlertEvent(
                "Apple", "BBG000B9XRY4", new BigDecimal("210"), new BigDecimal("200"),
                PriceAlertEvent.AlertType.SELL, userId, null, null);
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));

        service.handlePriceAlert(alert);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo(Notification.AlertType.SELL);
    }

    // helpers

    private AppUser user(UUID id, String email, String passwordHash, Long chatId) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash(passwordHash);
        if (chatId != null) {
            u.setTelegramId(chatId);
            u.setChatId(chatId);
        }
        return u;
    }

    private PriceAlertEvent buyAlert(UUID userId, Long telegramId, Long chatId) {
        return new PriceAlertEvent(
                "Apple", "BBG000B9XRY4",
                new BigDecimal("95"), new BigDecimal("100"),
                PriceAlertEvent.AlertType.BUY,
                userId, telegramId, chatId);
    }
}
