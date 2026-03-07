package ru.tuganov.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.TrackedInstrumentRequest;
import ru.tuganov.dto.TrackedInstrumentResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.exception.ResourceNotFoundException;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.TrackedInstrumentRepository;
import ru.tuganov.service.TrackedInstrumentService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackedInstrumentServiceIT extends BaseIntegrationTest {

    @Autowired
    TrackedInstrumentService tiService;
    @Autowired
    TrackedInstrumentRepository tiRepo;
    @Autowired
    AppUserRepository userRepo;

    private AppUser user;
    private AppUser otherUser;

    @BeforeEach
    void setUp() {
        user = userRepo.save(appUser());
        otherUser = userRepo.save(appUser());
    }

    @AfterEach
    void cleanup() {
        tiRepo.deleteAll();
        userRepo.deleteAll();
    }

    private AppUser appUser() {
        AppUser u = new AppUser();
        u.setCreatedAt(Instant.now());
        return u;
    }

    private TrackedInstrumentRequest request(String figi, String buy, String sell) {
        return new TrackedInstrumentRequest(figi, "Instrument " + figi,
                new BigDecimal(buy), new BigDecimal(sell), null, null);
    }

    @Test
    void createForUser_persistsInstrumentAndReturnsResponse() {
        TrackedInstrumentResponse response = tiService.createForUser(user, request("FIGI1", "90", "110"));

        assertThat(response.figi()).isEqualTo("FIGI1");
        assertThat(response.appUserId()).isEqualTo(user.getId());
        assertThat(response.buyAlertSent()).isFalse();
        assertThat(response.sellAlertSent()).isFalse();
        assertThat(tiRepo.count()).isEqualTo(1);
    }

    @Test
    void createForUser_buyPriceEqualToSellPrice_throwsIllegalArgument() {
        assertThatThrownBy(() -> tiService.createForUser(user, request("FIGI2", "100", "100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Цена покупки");
    }

    @Test
    void createForUser_buyPriceGreaterThanSellPrice_throwsIllegalArgument() {
        assertThatThrownBy(() -> tiService.createForUser(user, request("FIGI3", "120", "100")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getByAppUser_returnsOnlyOwnedInstruments() {
        tiService.createForUser(user, request("FIGI_OWN", "80", "120"));
        tiService.createForUser(otherUser, request("FIGI_OTHER", "50", "100"));

        List<TrackedInstrumentResponse> results = tiService.getByAppUser(user);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).figi()).isEqualTo("FIGI_OWN");
    }

    @Test
    void getById_correctOwner_returnsResponse() {
        TrackedInstrumentResponse created = tiService.createForUser(user, request("FIGI_GET", "90", "110"));

        TrackedInstrumentResponse found = tiService.getById(created.id(), user.getId());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.figi()).isEqualTo("FIGI_GET");
    }

    @Test
    void getById_wrongOwner_throwsForbidden() {
        TrackedInstrumentResponse created = tiService.createForUser(user, request("FIGI_FORBID", "90", "110"));

        assertThatThrownBy(() -> tiService.getById(created.id(), otherUser.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void getById_notFound_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> tiService.getById(UUID.randomUUID(), user.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_changesPricesAndResetsAlertFlags() {
        TrackedInstrumentResponse created = tiService.createForUser(user, request("FIGI_UPD", "90", "110"));

        // Manually set alert flags in DB
        TrackedInstrument ti = tiRepo.findById(created.id()).orElseThrow();
        ti.setBuyAlertSent(true);
        ti.setSellAlertSent(true);
        tiRepo.save(ti);

        TrackedInstrumentResponse updated = tiService.update(
                created.id(), request("FIGI_UPD", "85", "115"), user.getId());

        assertThat(updated.buyPrice()).isEqualByComparingTo("85");
        assertThat(updated.sellPrice()).isEqualByComparingTo("115");
        assertThat(updated.buyAlertSent()).isFalse();
        assertThat(updated.sellAlertSent()).isFalse();
    }

    @Test
    void update_wrongOwner_throwsForbidden() {
        TrackedInstrumentResponse created = tiService.createForUser(user, request("FIGI_UPD2", "90", "110"));

        assertThatThrownBy(() -> tiService.update(created.id(), request("FIGI_UPD2", "80", "120"), otherUser.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void delete_correctOwner_removesInstrument() {
        TrackedInstrumentResponse created = tiService.createForUser(user, request("FIGI_DEL", "90", "110"));

        tiService.delete(created.id(), user.getId());

        assertThat(tiRepo.findById(created.id())).isEmpty();
    }

    @Test
    void delete_wrongOwner_throwsForbidden() {
        TrackedInstrumentResponse created = tiService.createForUser(user, request("FIGI_DEL2", "90", "110"));

        assertThatThrownBy(() -> tiService.delete(created.id(), otherUser.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));

        assertThat(tiRepo.findById(created.id())).isPresent();
    }
}
