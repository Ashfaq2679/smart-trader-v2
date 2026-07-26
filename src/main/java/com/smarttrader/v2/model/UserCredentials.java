package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document storing per-user Coinbase Advanced Trade credentials.
 *
 * Ported from smart-trader-v1's com.techcobber.smarttrader.v1.models.UserCredentials
 * (per the user's request to keep v2 independent of the v1 jar rather than depend on
 * it) - same shape, native v2 package.
 *
 * Credentials are encrypted at rest using AES-GCM via
 * {@link com.smarttrader.v2.client.CredentialEncryptionService}. The
 * encryptedCredentials field is never stored in plain text.
 */
@Data
@Document("user_credentials")
public class UserCredentials {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String encryptedCredentials;

    private Instant createdAt;

    private Instant updatedAt;
}
