package com.meditriaje.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CsrfHeaderFilterTest {

    private CsrfHeaderFilter csrfHeaderFilter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        csrfHeaderFilter = new CsrfHeaderFilter(new ObjectMapper());
    }

    @Test
    void doFilter_peticionGet_pasaSinRequerirCabecera() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        csrfHeaderFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_peticionPostARutaExenta_pasaSinRequerirCabecera() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        csrfHeaderFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_peticionPostARutaProtegidaSinCabecera_retorna403Forbidden() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/appointments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        csrfHeaderFilter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_REQUERIDO");
    }

    @Test
    void doFilter_peticionPostConCabeceraXRequestedWith_pasaFiltro() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/appointments");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        csrfHeaderFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_peticionPostConCabeceraXCsrfProtection_pasaFiltro() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/appointments");
        request.addHeader("X-CSRF-Protection", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        csrfHeaderFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
