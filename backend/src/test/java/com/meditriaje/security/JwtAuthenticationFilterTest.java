package com.meditriaje.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_sinToken_continuaSinAutenticar() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_conCookieAccessTokenValida_autenticaUsuario() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients/me");
        request.setCookies(new Cookie("access_token", "valid.access.jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.esValido("valid.access.jwt")).thenReturn(true);
        when(jwtService.extraerPublicId("valid.access.jwt")).thenReturn("user-public-uuid");
        when(jwtService.extraerRoles("valid.access.jwt")).thenReturn(List.of("ROLE_PACIENTE"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("user-public-uuid");
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_PACIENTE");
    }

    @Test
    void doFilter_conTokenExpiradoOInvalido_noAutentica() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients/me");
        request.setCookies(new Cookie("access_token", "expired.jwt"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.esValido("expired.jwt")).thenReturn(false);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_conHeaderAuthorizationBearerValido_autentica() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients/me");
        request.addHeader("Authorization", "Bearer bearer.access.jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.esValido("bearer.access.jwt")).thenReturn(true);
        when(jwtService.extraerPublicId("bearer.access.jwt")).thenReturn("user-public-uuid");
        when(jwtService.extraerRoles("bearer.access.jwt")).thenReturn(List.of("ROLE_PACIENTE"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("user-public-uuid");
    }
}
