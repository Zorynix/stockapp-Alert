package ru.tuganov.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.tuganov.entity.AppUser;
import ru.tuganov.security.AppUserDetails;
import ru.tuganov.security.AppUserDetailsService;
import ru.tuganov.security.JwtTokenService;
import ru.tuganov.service.ReportService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ReportControllerTest {

    @Autowired private WebApplicationContext wac;

    @MockitoBean private ReportService reportService;
    @MockitoBean private JwtTokenService jwtTokenService;
    @MockitoBean private AppUserDetailsService appUserDetailsService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private AppUserDetails userDetails() {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        return new AppUserDetails(u);
    }

    @Test
    void generateReport_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/reports/stock")
                        .param("figi", "BBG000B9XRY4")
                        .param("name", "Apple"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generateReport_pdfFormat_returnsCorrectHeaders() throws Exception {
        byte[] pdfBytes = "%PDF-1.4 minimal".getBytes();
        when(reportService.generateStockReport("BBG000B9XRY4", "Apple", "1m", "pdf"))
                .thenReturn(pdfBytes);

        mvc.perform(get("/api/reports/stock")
                        .with(user(userDetails()))
                        .param("figi", "BBG000B9XRY4")
                        .param("name", "Apple")
                        .param("period", "1m")
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void generateReport_mdFormat_returnsCorrectHeaders() throws Exception {
        byte[] mdBytes = "# Report".getBytes();
        when(reportService.generateStockReport("BBG000B9XRY4", "Apple", "1m", "md"))
                .thenReturn(mdBytes);

        mvc.perform(get("/api/reports/stock")
                        .with(user(userDetails()))
                        .param("figi", "BBG000B9XRY4")
                        .param("name", "Apple")
                        .param("period", "1m")
                        .param("format", "md"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain"));
    }

    @Test
    void generateReport_invalidPeriod_returns400() throws Exception {
        mvc.perform(get("/api/reports/stock")
                        .with(user(userDetails()))
                        .param("figi", "BBG000B9XRY4")
                        .param("name", "Apple")
                        .param("period", "99y"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateReport_invalidFormat_returns400() throws Exception {
        mvc.perform(get("/api/reports/stock")
                        .with(user(userDetails()))
                        .param("figi", "BBG000B9XRY4")
                        .param("name", "Apple")
                        .param("format", "xlsx"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateReport_defaultParams_uses1mAndPdf() throws Exception {
        byte[] pdfBytes = "%PDF".getBytes();
        when(reportService.generateStockReport(eq("BBG000B9XRY4"), eq("Apple"), eq("1m"), eq("pdf")))
                .thenReturn(pdfBytes);

        mvc.perform(get("/api/reports/stock")
                        .with(user(userDetails()))
                        .param("figi", "BBG000B9XRY4")
                        .param("name", "Apple"))
                .andExpect(status().isOk());

        verify(reportService).generateStockReport("BBG000B9XRY4", "Apple", "1m", "pdf");
    }
}
