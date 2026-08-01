package com.esplus.security.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM for encrypting sensitive fields (e.g. BCrypt hashes) at rest.
 */
public final class AesCipherService {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesCipherService(SecretKey key) {
        this.key = key;
    }

    public static SecretKey generateKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(KEY_BITS, new SecureRandom());
        return generator.generateKey();
    }

    public static SecretKey fromBytes(byte[] raw) {
        return new SecretKeySpec(Arrays.copyOf(raw, raw.length), "AES");
    }

    public byte[] keyBytes() {
        return key.getEncoded();
    }

    public String encryptToBase64(String plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        buffer.put(iv);
        buffer.put(ciphertext);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public String decryptFromBase64(String encoded) throws GeneralSecurityException {
        byte[] packed = Base64.getDecoder().decode(encoded);
        if (packed.length <= IV_BYTES) {
            throw new GeneralSecurityException("Ciphertext too short");
        }

        byte[] iv = Arrays.copyOfRange(packed, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(packed, IV_BYTES, packed.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = cipher.doFinal(ciphertext);
        return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    }
}
