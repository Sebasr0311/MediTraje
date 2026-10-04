package com.meditriaje.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * Servicio de Autenticación de Contraseñas de Un Solo Uso Basadas en Tiempo (TOTP, RFC 6238, ADR-014).
 * Compatible con Google Authenticator, Microsoft Authenticator y RFC 6238 / RFC 4226.
 */
@Service
public class TotpService {

    private static final String HMAC_ALGO = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int OTP_DIGITS = 6;
    private static final int DIGITS_MODULO = 1_000_000;
    private static final int SECRET_BYTES = 20; // 160 bits

    /**
     * Genera un nuevo secreto criptográfico aleatorio en Base32.
     */
    public String generarNuevoSecreto() {
        return Base32Util.generarSecreto(SECRET_BYTES);
    }

    /**
     * Genera el código TOTP numérico de 6 dígitos para un instante dado.
     */
    public String generarCodigo(String secretBase32, Instant instant) {
        long timeStep = instant.getEpochSecond() / TIME_STEP_SECONDS;
        return generarCodigoParaPaso(secretBase32, timeStep);
    }

    /**
     * Valida un código TOTP ingresado por el usuario considerando una ventana de tolerancia de ±1 paso (±30s).
     */
    public boolean validarCodigo(String secretBase32, String codigoOtp, Instant instant) {
        if (secretBase32 == null || secretBase32.isBlank() || codigoOtp == null || codigoOtp.isBlank()) {
            return false;
        }

        String codigoLimpio = codigoOtp.trim();
        if (!codigoLimpio.matches("^[0-9]{6}$")) {
            return false;
        }

        long pasoActual = instant.getEpochSecond() / TIME_STEP_SECONDS;

        // Tolerancia a deriva horaria: paso anterior (-30s), actual (0s), paso siguiente (+30s)
        for (long paso = pasoActual - 1; paso <= pasoActual + 1; paso++) {
            String esperado = generarCodigoParaPaso(secretBase32, paso);
            if (MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), codigoLimpio.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Genera la URI estándar otpauth:// compatible con lectores QR de autenticadores móviles.
     */
    public String generarOtpAuthUri(String issuer, String accountName, String secretBase32) {
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedAccount = URLEncoder.encode(accountName, StandardCharsets.UTF_8).replace("+", "%20");

        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                encodedIssuer, encodedAccount, secretBase32, encodedIssuer, OTP_DIGITS, TIME_STEP_SECONDS
        );
    }

    private String generarCodigoParaPaso(String secretBase32, long timeStep) {
        byte[] keyBytes = Base32Util.decode(secretBase32);
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGO));
            byte[] hash = mac.doFinal(data);

            // Truncamiento dinámico RFC 4226
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % DIGITS_MODULO;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al calcular HMAC-SHA1 para TOTP", e);
        }
    }
}
