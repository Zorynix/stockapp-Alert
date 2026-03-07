package ru.tuganov.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.Notification;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.NotificationRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class NotificationRepositoryIT extends BaseIntegrationTest {

    @Autowired
    NotificationRepository notifRepo;
    @Autowired
    AppUserRepository userRepo;

    private AppUser user1;
    private AppUser user2;

    @BeforeEach
    void createUsers() {
        user1 = userRepo.save(appUser(111L));
        user2 = userRepo.save(appUser(222L));
    }

    private AppUser appUser(Long telegramId) {
        AppUser u = new AppUser();
        u.setTelegramId(telegramId);
        u.setChatId(telegramId);
        u.setCreatedAt(Instant.now());
        return u;
    }

    private Notification notification(AppUser user, Notification.AlertType type) {
        Notification n = new Notification();
        n.setAppUser(user);
        n.setUserId(user.getTelegramId());
        n.setChatId(user.getChatId());
        n.setInstrumentName("Apple");
        n.setFigi("BBG000B9XRY4");
        n.setAlertType(type);
        n.setCurrentPrice(new BigDecimal("95.00"));
        n.setThreshold(new BigDecimal("90.00"));
        n.setMessage("Alert triggered");
        n.setSentToTelegram(true);
        return notifRepo.save(n);
    }

    @Test
    void findTop50ByAppUser_returnsOnlyForThatUser() {
        notification(user1, Notification.AlertType.BUY);
        notification(user1, Notification.AlertType.SELL);
        notification(user2, Notification.AlertType.BUY);

        List<Notification> results = notifRepo.findTop50ByAppUserOrderByCreatedAtDesc(user1);

        assertThat(results).hasSize(2)
                .allSatisfy(n -> assertThat(n.getAppUser().getId()).isEqualTo(user1.getId()));
    }

    @Test
    void findTop50ByAppUser_returnsEmptyWhenNone() {
        assertThat(notifRepo.findTop50ByAppUserOrderByCreatedAtDesc(user1)).isEmpty();
    }

    @Test
    void findTop50ByUserId_legacyField_returnsCorrectUser() {
        notification(user1, Notification.AlertType.BUY);
        notification(user2, Notification.AlertType.SELL);

        List<Notification> results = notifRepo.findTop50ByUserIdOrderByCreatedAtDesc(111L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUserId()).isEqualTo(111L);
    }

    @Test
    void findTop50ByAppUser_limitsTo50() {
        for (int i = 0; i < 55; i++) {
            notification(user1, Notification.AlertType.BUY);
        }
        notifRepo.flush();

        List<Notification> results = notifRepo.findTop50ByAppUserOrderByCreatedAtDesc(user1);

        assertThat(results).hasSize(50);
    }

    @Test
    void save_setsCreatedAtViaPrePersist() {
        Instant before = Instant.now().minusSeconds(1);
        Notification n = notification(user1, Notification.AlertType.BUY);
        notifRepo.flush();

        Notification found = notifRepo.findById(n.getId()).orElseThrow();
        assertThat(found.getCreatedAt()).isAfter(before);
    }
}
