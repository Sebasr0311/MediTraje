package com.meditriaje.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String TEST_SECRET = "0123456789012345678901234567890123456789"; // > 256 bits
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 15);
    }

    @Test
    void constructor_secretoCorto_lanzaExcepcion() {
        assertThatThrownBy(() -> new JwtService("secreto-demasiado-corto", 15))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 256 bits");
    }

    @Test
    void generarAccessToken_creaTokenValidoConSubjectYClaims() {
        String publicId = "d7c80521-4cb5-45dc-9081-4ebdf768f771";
        String email = "paciente@example.com";
        List<String> roles = List.of("ROLE_PACIENTE");

        String token = jwtService.generarAccessToken(publicId, email, roles);

        assertThat(token).isNotBlank();
        assertThat(jwtService.esValido(token)).isTrue();
        assertThat(jwtService.extraerPublicId(token)).isEqualTo(publicId);
        assertThat(jwtService.extraerRoles(token)).containsExactly("ROLE_PACIENTE");

        Claims claims = jwtService.extraerClaims(token);
        assertThat(claims.get("email", String.class)).isEqualTo(email);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void esValido_tokenInvalidoOMalformado_retornaFalse() {
        assertThat(jwtService.esValido("token.invalido.malformado")).isFalse();
        assertThat(jwtService.esValido(null)).isFalse();
        assertThat(jwtService.esValido("")).isFalse();
    }

    @Test
    void generarMfaChallengeToken_creaChallengeValidoYRechazadoPorEsValido() {
        String publicId = "user-mfa-123";
        String email = "medico@example.com";
        List<String> roles = List.of("ROLE_PROFESIONAL");

        String challengeToken = jwtService.generarMfaChallengeToken(publicId, email, roles);

        assertThat(challengeToken).isNotBlank();
        assertThat(jwtService.esMfaChallengeValido(challengeToken)).isTrue();
        assertThat(jwtService.esValido(challengeToken)).isFalse(); // No debe ser aceptado como access token de sesión
        assertThat(jwtService.esAccessToken(challengeToken)).isFalse();
        assertThat(jwtService.extraerPublicId(challengeToken)).isEqualTo(publicId);
    }
}
