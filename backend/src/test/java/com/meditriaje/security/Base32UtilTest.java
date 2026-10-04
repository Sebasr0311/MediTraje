package com.meditriaje.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base32UtilTest {

    @Test
    void encodeYDecode_coincidenConVectoresRfc4648() {
        // Vectores RFC 4648 sección 10
        assertThat(Base32Util.encode("".getBytes(StandardCharsets.UTF_8))).isEqualTo("");
        assertThat(Base32Util.encode("f".getBytes(StandardCharsets.UTF_8))).isEqualTo("MY");
        assertThat(Base32Util.encode("fo".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXQ");
        assertThat(Base32Util.encode("foo".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6");
        assertThat(Base32Util.encode("foob".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6YQ");
        assertThat(Base32Util.encode("fooba".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6YTB");
        assertThat(Base32Util.encode("foobar".getBytes(StandardCharsets.UTF_8))).isEqualTo("MZXW6YTBOI");

        assertThat(new String(Base32Util.decode("MZXW6YTB"), StandardCharsets.UTF_8)).isEqualTo("fooba");
        assertThat(new String(Base32Util.decode("mzxw6ytb"), StandardCharsets.UTF_8)).isEqualTo("fooba");
        assertThat(new String(Base32Util.decode("MZXW6YTB==="), StandardCharsets.UTF_8)).isEqualTo("fooba");
        assertThat(new String(Base32Util.decode("MZXW-6YTB"), StandardCharsets.UTF_8)).isEqualTo("fooba");
    }

    @Test
    void generarSecreto_generaCadenaBase32DeLongitudEsperada() {
        String secret = Base32Util.generarSecreto(20);

        assertThat(secret).hasSize(32);
        assertThat(secret).matches("^[A-Z2-7]{32}$");

        byte[] decoded = Base32Util.decode(secret);
        assertThat(decoded).hasSize(20);
    }

    @Test
    void decode_conCaracterInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> Base32Util.decode("MZXW6Y8!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalido");
    }
}
