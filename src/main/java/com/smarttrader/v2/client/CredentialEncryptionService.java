package com.smarttrader.v2.client;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Encrypts and decrypts per-user Coinbase credentials using AES-GCM, for
 * CoinbaseClientFactory's multi-user client cache.
 *
 * Ported from smart-trader-v1's
 * com.techcobber.smarttrader.v1.services.CredentialEncryptionService (per the user's
 * request to keep v2 independent of the v1 jar rather than depend on it) - same
 * approach, native v2 package: the symmetric key is supplied via the
 * CREDENTIAL_ENCRYPTION_KEY environment variable (Base64-encoded, 256-bit), each
 * encryption generates a unique 12-byte IV prepended to the ciphertext, and the
 * application refuses to start without the key configured so credentials can never be
 * stored insecurely by default.
 */
@Service
@Slf4j
public class CredentialEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;

    public CredentialEncryptionService(@Value("${CREDENTIAL_ENCRYPTION_KEY:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "CREDENTIAL_ENCRYPTION_KEY is not set. "
                            + "The application cannot start without a Base64-encoded 256-bit encryption key. "
                            + "Set the CREDENTIAL_ENCRYPTION_KEY environment variable.");
        }
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        this.secretKey = new SecretKeySpec(decodedKey, "AES");
    }

    /**
     * Encrypts the given plain-text credentials.
     *
     * @param plainText the raw credential string
     * @return Base64-encoded ciphertext (IV + encrypted bytes)
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt credentials", e);
        }
    }

    /**
     * Decrypts a previously encrypted credential string.
     *
     * @param cipherText Base64-encoded ciphertext (IV + encrypted bytes)
     * @return the original plain-text credential string
     */
    public String decrypt(String cipherText) {
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt credentials", e);
        }
    }
}
