package com.esplus.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CryptoRoundTripTest {
    @Test
    void bcryptAesRsaRoundTrip(@TempDir Path temp) throws Exception {
        RsaKeyManager rsa = new RsaKeyManager(temp.resolve("keys"));
        rsa.initialize();
        assertTrue(Files.exists(temp.resolve("keys").resolve("private.pem")));

        SecretKey aesKey = AesCipherService.generateKey();
        byte[] wrapped = rsa.wrapAesKey(aesKey);
        SecretKey unwrapped = rsa.unwrapAesKey(wrapped);
        assertEquals(aesKey, unwrapped);

        AesCipherService aes = new AesCipherService(unwrapped);
        BcryptPasswordService bcrypt = new BcryptPasswordService();
        String hash = bcrypt.hash("SudoPass-123");
        String cipher = aes.encryptToBase64(hash);
        String restored = aes.decryptFromBase64(cipher);
        assertTrue(bcrypt.matches("SudoPass-123", restored));
        assertFalse(bcrypt.matches("wrong", restored));
    }
}
