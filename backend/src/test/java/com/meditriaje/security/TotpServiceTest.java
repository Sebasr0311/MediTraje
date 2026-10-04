package com.meditriaje.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    @Test
    void generarNuevoSecreto_retornaSecretoBase32De32Caracteres() {
        String secreto = totpService.generarNuevoSecreto();

        assertThat(secreto).isNotNull().hasSize(32);
        assertThat(secreto).matches("^[A-Z2-7]{32}$");
    }

    @Test
    void generarCodigo_retornaCodigoNumericoDe6Digitos() {
        String secreto = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
        Instant ahora = Instant.now();

        String codigo = totpService.generarCodigo(secreto, ahora);

        assertThat(codigo).isNotNull().hasSize(6);
        assertThat(codigo).matches("^[0-9]{6}$");
    }

    @Test
    void validarCodigo_aceptaCodigoValidoEnMismoPaso() {
        String secreto = totpService.generarNuevoSecreto();
        Instant ahora = Instant.ofEpochSecond(1700000000L); // Paso exacto

        String codigo = totpService.generarCodigo(secreto, ahora);

        assertThat(totpService.validarCodigo(secreto, codigo, ahora)).isTrue();
    }

    @Test
    void validarCodigo_aceptaCodigoConDerivaDeUnPaso() {
        String secreto = totpService.generarNuevoSecreto();
        Instant t0 = Instant.ofEpochSecond(1700000000L);
        Instant tMenos25s = t0.minusSeconds(25); // Paso anterior (-30s ventana)
        Instant tMas25s = t0.plusSeconds(25);   // Paso siguiente (+30s ventana)

        String codigo = totpService.generarCodigo(secreto, t0);

        assertThat(totpService.validarCodigo(secreto, codigo, tMenos25s)).isTrue();
        assertThat(totpService.validarCodigo(secreto, codigo, tMas25s)).isTrue();
    }

    @Test
    void validarCodigo_rechazaCodigoFueraDeVentana() {
        String secreto = totpService.generarNuevoSecreto();
        Instant t0 = Instant.ofEpochSecond(1700000000L);
        Instant tFuera = t0.plusSeconds(90); // 3 pasos después

        String codigoAntiguo = totpService.generarCodigo(secreto, t0);

        assertThat(totpService.validarCodigo(secreto, codigoAntiguo, tFuera)).isFalse();
    }

    @Test
    void validarCodigo_rechazaCodigoFormatoInvalidoOIncorrecto() {
        String secreto = totpService.generarNuevoSecreto();
        Instant ahora = Instant.now();

        assertThat(totpService.validarCodigo(secreto, "000000", ahora)).isFalse();
        assertThat(totpService.validarCodigo(secreto, "abc", ahora)).isFalse();
        assertThat(totpService.validarCodigo(secreto, null, ahora)).isFalse();
        assertThat(totpService.validarCodigo(null, "123456", ahora)).isFalse();
    }

    @Test
    void generarOtpAuthUri_construyeUriEstandarCompatible() {
        String uri = totpService.generarOtpAuthUri("MediTriaje", "dr.perez@hospital.com", "JBSWY3DPEHPK3PXP");

        assertThat(uri).startsWith("otpauth://totp/MediTriaje:dr.perez%40hospital.com");
        assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP");
        assertThat(uri).contains("issuer=MediTriaje");
        assertThat(uri).contains("algorithm=SHA1");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }
}
