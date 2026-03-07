package ru.tuganov.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.tuganov.dto.TrackedInstrumentRequest;
import ru.tuganov.dto.TrackedInstrumentResponse;
import ru.tuganov.entity.AppUser;
import ru.tuganov.security.AppUserDetails;
import ru.tuganov.security.AppUserDetailsService;
import ru.tuganov.security.JwtTokenService;
import ru.tuganov.service.TrackedInstrumentService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class TrackedInstrumentControllerTest {

    @Autowired private WebApplicationContext wac;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private TrackedInstrumentService trackedInstrumentService;
    @MockitoBean private JwtTokenService jwtTokenService;
    @MockitoBean private AppUserDetailsService appUserDetailsService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private AppUserDetails userDetails(UUID id) {
        AppUser u = new AppUser();
        u.setId(id);
        return new AppUserDetails(u);
    }

    private TrackedInstrumentResponse tiResponse(UUID id, UUID userId) {
        return new TrackedInstrumentResponse(id, "BBG000B9XRY4", "Apple",
                new BigDecimal("90"), new BigDecimal("110"),
                false, false, Instant.now(), userId);
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/api/tracked-instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_authenticated_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID instId = UUID.randomUUID();
        when(trackedInstrumentService.createForUser(any(AppUser.class), any(TrackedInstrumentRequest.class)))
                .thenReturn(tiResponse(instId, userId));

        mvc.perform(post("/api/tracked-instruments")
                        .with(user(userDetails(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "figi", "BBG000B9XRY4",
                                "instrumentName", "Apple",
                                "buyPrice", "90.00",
                                "sellPrice", "110.00"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.figi").value("BBG000B9XRY4"));
    }

    @Test
    void create_missingFigi_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/tracked-instruments")
                        .with(user(userDetails(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instrumentName", "Apple",
                                "buyPrice", "90.00",
                                "sellPrice", "110.00"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_authenticated_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(trackedInstrumentService.getByAppUser(any(AppUser.class)))
                .thenReturn(List.of(tiResponse(UUID.randomUUID(), userId)));

        mvc.perform(get("/api/tracked-instruments")
                        .with(user(userDetails(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].figi").value("BBG000B9XRY4"));
    }

    @Test
    void getById_authenticated_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID instId = UUID.randomUUID();
        when(trackedInstrumentService.getById(instId, userId)).thenReturn(tiResponse(instId, userId));

        mvc.perform(get("/api/tracked-instruments/" + instId)
                        .with(user(userDetails(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instId.toString()));
    }

    @Test
    void update_authenticated_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID instId = UUID.randomUUID();
        when(trackedInstrumentService.update(eq(instId), any(), eq(userId)))
                .thenReturn(tiResponse(instId, userId));

        mvc.perform(put("/api/tracked-instruments/" + instId)
                        .with(user(userDetails(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "figi", "BBG000B9XRY4",
                                "instrumentName", "Apple",
                                "buyPrice", "80.00",
                                "sellPrice", "120.00"
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    void delete_authenticated_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID instId = UUID.randomUUID();
        doNothing().when(trackedInstrumentService).delete(instId, userId);

        mvc.perform(delete("/api/tracked-instruments/" + instId)
                        .with(user(userDetails(userId))))
                .andExpect(status().isNoContent());
    }
}
