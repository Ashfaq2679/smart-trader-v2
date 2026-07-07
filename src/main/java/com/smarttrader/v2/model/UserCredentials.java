package com.smarttrader.v2.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * MongoDB document storing per-user Coinbase Advanced Trade credentials.
 *
 * <p>Credentials are encrypted at rest using AES-GCM via
 * {@link com.techcobber.smarttrader.v1.services.CredentialEncryptionService}.
 * The {@code encryptedCredentials} field is <b>never</b> stored in plain text.</p>
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
