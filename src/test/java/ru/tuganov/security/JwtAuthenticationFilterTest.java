package ru.tuganov.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.tuganov.entity.AppUser;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenService jwtTokenService;
    @Mock private AppUserDetailsService userDetailsService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() { SecurityContextHolder.clearContext(); }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); }

    @Test
    void doFilter_noHeader_continues() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_notBearer_continues() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic abc");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_validToken_setsAuth() throws Exception {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        AppUserDetails details = new AppUserDetails(user);
        when(request.getHeader("Authorization")).thenReturn("Bearer token123");
        when(jwtTokenService.validateAndExtractUserId("token123")).thenReturn(Optional.of(userId));
        when(userDetailsService.loadUserByUsername(userId.toString())).thenReturn(details);
        filter.doFilterInternal(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void doFilter_invalidToken_noAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad");
        when(jwtTokenService.validateAndExtractUserId("bad")).thenReturn(Optional.empty());
        filter.doFilterInternal(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_userNotFound_noAuth() throws Exception {
        UUID userId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtTokenService.validateAndExtractUserId("tok")).thenReturn(Optional.of(userId));
        when(userDetailsService.loadUserByUsername(userId.toString())).thenThrow(new UsernameNotFoundException("x"));
        filter.doFilterInternal(request, response, filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
