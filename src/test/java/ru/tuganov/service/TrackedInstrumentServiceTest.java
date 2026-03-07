package ru.tuganov.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import ru.tuganov.dto.TrackedInstrumentRequest;
import ru.tuganov.dto.TrackedInstrumentResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.TrackedInstrument;
import ru.tuganov.exception.ResourceNotFoundException;
import ru.tuganov.repository.TrackedInstrumentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackedInstrumentServiceTest {

    @Mock private TrackedInstrumentRepository trackedInstrumentRepository;
    @InjectMocks private TrackedInstrumentService service;

    @Test
    void create_validPrices_returnsResponse() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrumentRequest req = request("BBG000B9XRY4", "Apple", "90.00", "110.00");
        TrackedInstrument saved = instrument(UUID.randomUUID(), user, "90.00", "110.00");
        when(trackedInstrumentRepository.save(any())).thenReturn(saved);

        TrackedInstrumentResponse res = service.createForUser(user, req);

        assertThat(res.figi()).isEqualTo("BBG000B9XRY4");
        assertThat(res.appUserId()).isEqualTo(user.getId());
    }

    @Test
    void create_buyPriceGeqSellPrice_throwsIllegalArgument() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrumentRequest req = request("BBG000B9XRY4", "Apple", "110.00", "90.00");

        assertThatThrownBy(() -> service.createForUser(user, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getByAppUser_returnsAllForUser() {
        AppUser user = user(UUID.randomUUID());
        TrackedInstrument ti = instrument(UUID.randomUUID(), user, "90", "110");
        when(trackedInstrumentRepository.findAllByUser(user)).thenReturn(List.of(ti));

        List<TrackedInstrumentResponse> result = service.getByAppUser(user);

        assertThat(result).hasSize(1);
    }

    @Test
    void getById_owner_returnsResponse() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId);
        UUID instId = UUID.randomUUID();
        TrackedInstrument ti = instrument(instId, user, "90", "110");
        when(trackedInstrumentRepository.findById(instId)).thenReturn(Optional.of(ti));

        TrackedInstrumentResponse res = service.getById(instId, userId);

        assertThat(res.id()).isEqualTo(instId);
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        UUID instId = UUID.randomUUID();
        when(trackedInstrumentRepository.findById(instId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(instId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_notOwner_throws403() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        AppUser owner = user(ownerId);
        UUID instId = UUID.randomUUID();
        TrackedInstrument ti = instrument(instId, owner, "90", "110");
        when(trackedInstrumentRepository.findById(instId)).thenReturn(Optional.of(ti));

        assertThatThrownBy(() -> service.getById(instId, otherId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(403);
    }

    @Test
    void update_validPrices_resetsBuyAndSellAlertFlags() {
        UUID userId = UUID.randomUUID();
        AppUser owner = user(userId);
        UUID instId = UUID.randomUUID();
        TrackedInstrument ti = instrument(instId, owner, "90", "110");
        ti.setBuyAlertSent(true);
        ti.setSellAlertSent(true);
        TrackedInstrumentRequest req = request("BBG000B9XRY4", "Apple", "80.00", "120.00");
        when(trackedInstrumentRepository.findById(instId)).thenReturn(Optional.of(ti));
        when(trackedInstrumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TrackedInstrumentResponse res = service.update(instId, req, userId);

        assertThat(res.buyAlertSent()).isFalse();
        assertThat(res.sellAlertSent()).isFalse();
    }

    @Test
    void update_invalidPrices_throwsIllegalArgument() {
        UUID userId = UUID.randomUUID();
        TrackedInstrumentRequest req = request("BBG000B9XRY4", "Apple", "200.00", "100.00");

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), req, userId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_owner_deletesInstrument() {
        UUID userId = UUID.randomUUID();
        AppUser owner = user(userId);
        UUID instId = UUID.randomUUID();
        TrackedInstrument ti = instrument(instId, owner, "90", "110");
        when(trackedInstrumentRepository.findById(instId)).thenReturn(Optional.of(ti));

        service.delete(instId, userId);

        verify(trackedInstrumentRepository).delete(ti);
    }

    @Test
    void delete_notOwner_throws403() {
        UUID ownerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        AppUser owner = user(ownerId);
        UUID instId = UUID.randomUUID();
        TrackedInstrument ti = instrument(instId, owner, "90", "110");
        when(trackedInstrumentRepository.findById(instId)).thenReturn(Optional.of(ti));

        assertThatThrownBy(() -> service.delete(instId, otherId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status.value").isEqualTo(403);
    }

    // helpers

    private AppUser user(UUID id) {
        AppUser u = new AppUser();
        u.setId(id);
        return u;
    }

    private TrackedInstrument instrument(UUID id, AppUser user, String buy, String sell) {
        TrackedInstrument ti = new TrackedInstrument();
        ti.setId(id);
        ti.setUser(user);
        ti.setFigi("BBG000B9XRY4");
        ti.setInstrumentName("Apple");
        ti.setBuyPrice(new BigDecimal(buy));
        ti.setSellPrice(new BigDecimal(sell));
        return ti;
    }

    private TrackedInstrumentRequest request(String figi, String name, String buy, String sell) {
        return new TrackedInstrumentRequest(figi, name,
                new BigDecimal(buy), new BigDecimal(sell), null, null);
    }
}
