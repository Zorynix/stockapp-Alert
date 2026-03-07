package ru.tuganov.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.TrackedInstrumentRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TrackedInstrumentRepositoryIT extends BaseIntegrationTest {

    @Autowired
    TrackedInstrumentRepository tiRepo;
    @Autowired
    AppUserRepository userRepo;

    private AppUser user1;
    private AppUser user2;

    @BeforeEach
    void createUsers() {
        user1 = userRepo.save(telegramUser(10L));
        user2 = userRepo.save(telegramUser(20L));
    }

    private AppUser telegramUser(long telegramId) {
        AppUser u = new AppUser();
        u.setTelegramId(telegramId);
        u.setChatId(telegramId);
        u.setCreatedAt(Instant.now());
        return u;
    }

    private TrackedInstrument instrument(AppUser user, String figi) {
        TrackedInstrument ti = new TrackedInstrument();
        ti.setFigi(figi);
        ti.setInstrumentName("Name " + figi);
        ti.setBuyPrice(new BigDecimal("90.00"));
        ti.setSellPrice(new BigDecimal("110.00"));
        ti.setUser(user);
        return tiRepo.save(ti);
    }

    @Test
    void findAllByUser_returnsOnlyForThatUser() {
        instrument(user1, "FIGI_A");
        instrument(user1, "FIGI_B");
        instrument(user2, "FIGI_C");

        List<TrackedInstrument> results = tiRepo.findAllByUser(user1);

        assertThat(results).hasSize(2)
                .allSatisfy(ti -> assertThat(ti.getUser().getId()).isEqualTo(user1.getId()));
    }

    @Test
    void findAllByUser_emptyWhenNoInstruments() {
        assertThat(tiRepo.findAllByUser(user1)).isEmpty();
    }

    @Test
    void findAllByUserTelegramId_matchesCorrectUser() {
        instrument(user1, "FIGI_TG");
        instrument(user2, "FIGI_OTHER");

        List<TrackedInstrument> results = tiRepo.findAllByUserTelegramId(10L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFigi()).isEqualTo("FIGI_TG");
    }

    @Test
    void findAllWithUsers_joinsUserData() {
        instrument(user1, "FIGI_JOIN");
        tiRepo.flush();

        List<TrackedInstrument> results = tiRepo.findAllWithUsers();

        assertThat(results).isNotEmpty();
        // Verify JOIN FETCH populated the user (no LazyInitializationException)
        results.forEach(ti -> assertThat(ti.getUser().getId()).isNotNull());
    }

    @Test
    void findAllWithUsers_includesAllUsers() {
        instrument(user1, "FIGI_U1");
        instrument(user2, "FIGI_U2");
        tiRepo.flush();

        List<TrackedInstrument> results = tiRepo.findAllWithUsers();

        assertThat(results).hasSize(2);
        assertThat(results.stream().map(ti -> ti.getUser().getId()))
                .containsExactlyInAnyOrder(user1.getId(), user2.getId());
    }

    @Test
    void delete_removesFromRepository() {
        TrackedInstrument ti = instrument(user1, "FIGI_DEL");
        UUID id = ti.getId();

        tiRepo.delete(ti);
        tiRepo.flush();

        assertThat(tiRepo.findById(id)).isEmpty();
    }

    @Test
    void alertSentFlags_defaultToFalse() {
        TrackedInstrument ti = instrument(user1, "FIGI_FLAGS");
        tiRepo.flush();

        TrackedInstrument found = tiRepo.findById(ti.getId()).orElseThrow();
        assertThat(found.isBuyAlertSent()).isFalse();
        assertThat(found.isSellAlertSent()).isFalse();
    }
}
