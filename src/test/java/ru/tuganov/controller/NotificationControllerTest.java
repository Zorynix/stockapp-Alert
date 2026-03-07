package ru.tuganov.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.Notification;
import ru.tuganov.repository.NotificationRepository;
import ru.tuganov.security.AppUserDetails;
import ru.tuganov.security.AppUserDetailsService;
import ru.tuganov.security.JwtTokenService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired private WebApplicationContext wac;

    @MockitoBean private NotificationRepository notificationRepository;
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

    @Test
    void getNotifications_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotifications_authenticated_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        Notification n = notification(user);
        when(notificationRepository.findTop50ByAppUserOrderByCreatedAtDesc(any(AppUser.class)))
                .thenReturn(List.of(n));

        mvc.perform(get("/api/notifications")
                        .with(user(userDetails(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instrumentName").value("Apple"));
    }

    @Test
    void getNotifications_authenticated_emptyList_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findTop50ByAppUserOrderByCreatedAtDesc(any()))
                .thenReturn(List.of());

        mvc.perform(get("/api/notifications")
                        .with(user(userDetails(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private Notification notification(AppUser user) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setAppUser(user);
        n.setInstrumentName("Apple");
        n.setFigi("BBG000B9XRY4");
        n.setAlertType(Notification.AlertType.BUY);
        n.setCurrentPrice(new BigDecimal("95"));
        n.setThreshold(new BigDecimal("100"));
        n.setMessage("Buy alert triggered");
        n.setSentToTelegram(false);
        n.setCreatedAt(Instant.now());
        return n;
    }
}
