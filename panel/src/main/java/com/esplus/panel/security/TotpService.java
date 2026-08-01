package com.esplus.panel.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Panel-side RFC 6238 TOTP (mirrors mod TotpService). */
public final class TotpService {
    private static final String HMAC = "HmacSHA1";
    private static final int DIGITS = 6;
    private static final long STEP_SECONDS = 30L;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpService() {
    }

    public static String generateSecret() {
        byte[] raw = new byte[20];
        new SecureRandom().nextBytes(raw);
        return encodeBase32(raw);
    }

    public static boolean verify(String base32Secret, String code) {
        return verify(base32Secret, code, System.currentTimeMillis() / 1000L, 1);
    }

    public static boolean verify(String base32Secret, String code, long unixSeconds, int window) {
        if (base32Secret == null || base32Secret.isBlank() || code == null) {
            return false;
        }
        String normalized = code.trim().replace(" ", "");
        if (!normalized.matches("\\d{6}")) {
            return false;
        }
        try {
            byte[] key = decodeBase32(base32Secret);
            long counter = unixSeconds / STEP_SECONDS;
            for (int i = -window; i <= window; i++) {
                if (generateCode(key, counter + i).equals(normalized)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public static String otpAuthUri(String issuer, String account, String secret) {
        String iss = issuer == null || issuer.isBlank() ? "ESPlus" : issuer.trim();
        String acc = account == null || account.isBlank() ? "user" : account.trim();
        return "otpauth://totp/" + urlEncode(iss) + ":" + urlEncode(acc)
                + "?secret=" + secret.replace(" ", "")
                + "&issuer=" + urlEncode(iss)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String generateCode(byte[] key, long counter) throws GeneralSecurityException {
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        Mac mac = Mac.getInstance(HMAC);
        mac.init(new SecretKeySpec(key, HMAC));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % 1_000_000);
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 31));
        }
        return sb.toString();
    }

    private static byte[] decodeBase32(String input) {
        String s = input.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("=", "");
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            int val = BASE32.indexOf(s.charAt(i));
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
