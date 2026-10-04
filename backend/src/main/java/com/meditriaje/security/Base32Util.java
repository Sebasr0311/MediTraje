package com.meditriaje.security;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Utilidad RFC 4648 para codificación y decodificación Base32 (ADR-014, F2.1.4).
 * Utilizado para el intercambio de secretos TOTP compatibles con Google/Microsoft Authenticator.
 */
public final class Base32Util {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] DECODE_TABLE = new int[128];
    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        for (int i = 0; i < DECODE_TABLE.length; i++) {
            DECODE_TABLE[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length(); i++) {
            char c = ALPHABET.charAt(i);
            DECODE_TABLE[c] = i;
            DECODE_TABLE[Character.toLowerCase(c)] = i;
        }
    }

    private Base32Util() {}

    /**
     * Genera un secreto aleatorio en Base32 del tamaño en bytes especificado.
     * Para TOTP RFC 6238 se recomiendan 20 bytes (160 bits = 32 caracteres Base32).
     */
    public static String generarSecreto(int numBytes) {
        if (numBytes <= 0) {
            throw new IllegalArgumentException("El numero de bytes debe ser positivo");
        }
        byte[] buffer = new byte[numBytes];
        RANDOM.nextBytes(buffer);
        return encode(buffer);
    }

    /**
     * Codifica un arreglo de bytes a una cadena Base32 sin padding.
     */
    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                result.append(ALPHABET.charAt(index));
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(ALPHABET.charAt(index));
        }

        return result.toString();
    }

    /**
     * Decodifica una cadena Base32 a su arreglo de bytes original.
     * Tolera minúsculas, mayúsculas, espacios, guiones y caracteres de padding '='.
     */
    public static byte[] decode(String base32) {
        if (base32 == null || base32.isBlank()) {
            return new byte[0];
        }

        String sanitized = base32.toUpperCase(Locale.ROOT)
                .replaceAll("[=\\s-]", "");

        int numBytes = (sanitized.length() * 5) / 8;
        byte[] result = new byte[numBytes];
        int buffer = 0;
        int bitsLeft = 0;
        int byteIndex = 0;

        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c >= DECODE_TABLE.length || DECODE_TABLE[c] == -1) {
                throw new IllegalArgumentException("Caracter Base32 invalido: " + c);
            }
            int val = DECODE_TABLE[c];
            buffer = (buffer << 5) | val;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                if (byteIndex < result.length) {
                    result[byteIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                }
                bitsLeft -= 8;
            }
        }

        return result;
    }
}
