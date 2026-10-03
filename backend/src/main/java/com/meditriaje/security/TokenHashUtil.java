package com.meditriaje.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utilidad criptográfica para hashear tokens opacos de refresco con SHA-256 (ADR-002).
 */
public final class TokenHashUtil {

    private TokenHashUtil() {}

    /**
     * Genera el hash SHA-256 en formato hexadecimal para persistencia en base de datos.
     */
    public static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("El token no puede ser nulo ni vacio");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
