package com.esplus.security.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Hashes and verifies passwords with BCrypt. Never stores plaintext.
 */
public final class BcryptPasswordService {
    private static final int LOG_ROUNDS = 12;

    public String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(LOG_ROUNDS, new SecureRandom()));
    }

    public boolean matches(String password, String bcryptHash) {
        if (password == null || bcryptHash == null || bcryptHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(password, bcryptHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static void wipe(char[] chars) {
        if (chars != null) {
            Arrays.fill(chars, '\0');
        }
    }

    public static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
