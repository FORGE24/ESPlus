package com.esplus.security.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/**
 * RSA-2048 keypair management and AES master-key wrapping.
 */
public final class RsaKeyManager {
    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int KEY_SIZE = 2048;

    private final Path keysDir;
    private final Path privateKeyPath;
    private final Path publicKeyPath;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public RsaKeyManager(Path keysDir) {
        this.keysDir = keysDir;
        this.privateKeyPath = keysDir.resolve("private.pem");
        this.publicKeyPath = keysDir.resolve("public.pem");
    }

    public void initialize() throws GeneralSecurityException, IOException {
        Files.createDirectories(keysDir);
        if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
            load();
        } else {
            generateAndPersist();
        }
    }

    public byte[] wrapAesKey(SecretKey aesKey) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(aesKey.getEncoded());
    }

    public SecretKey unwrapAesKey(byte[] wrapped) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] raw = cipher.doFinal(wrapped);
        return AesCipherService.fromBytes(raw);
    }

    public String signSha256Base64(byte[] payload) throws GeneralSecurityException {
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(payload);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    public boolean verifySha256Base64(byte[] payload, String signatureB64) throws GeneralSecurityException {
        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(payload);
        return signature.verify(Base64.getDecoder().decode(signatureB64));
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    private void generateAndPersist() throws GeneralSecurityException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        generator.initialize(KEY_SIZE);
        KeyPair pair = generator.generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();

        writePem(privateKeyPath, "PRIVATE KEY", privateKey.getEncoded());
        writePem(publicKeyPath, "PUBLIC KEY", publicKey.getEncoded());
    }

    private void load() throws GeneralSecurityException, IOException {
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        this.privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(readPem(privateKeyPath)));
        this.publicKey = factory.generatePublic(new X509EncodedKeySpec(readPem(publicKeyPath)));
    }

    private static void writePem(Path path, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        Files.writeString(path, pem);
    }

    private static byte[] readPem(Path path) throws IOException {
        String content = Files.readString(path)
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(content);
    }
}
