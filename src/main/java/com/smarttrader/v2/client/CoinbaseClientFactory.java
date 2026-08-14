package com.smarttrader.v2.client;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.credentials.CoinbaseAdvancedCredentials;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smarttrader.v2.model.UserCredentials;
import com.smarttrader.v2.repository.UserCredentialsRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Per-user Coinbase client factory with built-in caching.
 *
 * <p>Each user's {@link CoinbaseAdvancedClient} is created on demand, cached for
 * 30 minutes, and automatically evicted after that period. When a user's
 * credentials are updated or removed the cached client is invalidated
 * immediately.</p>
 *
 * <p><b>Design Pattern: Factory + Cache-Aside</b> — Callers obtain a client via
 * {@link #getClientForUser(String)} without knowing how it is created or cached.
 * The factory resolves encrypted credentials from MongoDB, decrypts them, and
 * builds a fresh {@link CoinbaseAdvancedClient} when no cached instance exists.</p>
 */
@Service
@Slf4j
public class CoinbaseClientFactory {

	private final UserCredentialsRepository credentialsRepository;
	private final CredentialEncryptionService encryptionService;

	private final Cache<String, CoinbaseAdvancedClient> clientCache;

	public CoinbaseClientFactory(
			UserCredentialsRepository credentialsRepository,
			CredentialEncryptionService encryptionService) {
		this.credentialsRepository = credentialsRepository;
		this.encryptionService = encryptionService;
		this.clientCache = Caffeine.newBuilder()
				.expireAfterWrite(30, TimeUnit.MINUTES)
				.maximumSize(500)
				.build();
		populateCacheOnStartup();
	}

	private void populateCacheOnStartup() {
		credentialsRepository.findAll().forEach(cred -> {
			String userId = cred.getUserId();
			try {
				CoinbaseAdvancedClient client = buildClient(userId);
				clientCache.put(userId, client);
				log.info("Preloaded Coinbase client for user [{}]", userId);
			} catch (Throwable t) {
				log.error("Failed to preload Coinbase client for user [{}]: {}", userId, t.getMessage());
			}
		});
		log.info("Coinbase client cache initialized with {} entries", clientCache);
	}
	
	public CoinbaseAdvancedClient getCoinbaseClientForUserFromCache(String userId) {
		if (userId == null || userId.isBlank()) {
			throw new IllegalArgumentException("userId must not be null or blank");
		}
		return clientCache.getIfPresent(userId);
	}

	/**
	 * Returns a {@link CoinbaseAdvancedClient} for the given user.
	 *
	 * <p>The client is retrieved from the cache when available; otherwise a new one
	 * is built from the user's stored (encrypted) credentials.</p>
	 *
	 * @param userId unique user identifier
	 * @return a ready-to-use Coinbase client
	 * @throws IllegalArgumentException if no credentials are stored for the user
	 * @throws RuntimeException         if the credentials cannot be decrypted or
	 *                                  the client cannot be created
	 */
	public CoinbaseAdvancedClient getClientForUser(String userId) {
		return clientCache.get(userId, this::buildClient);
	}

	/**
	 * Stores (or replaces) encrypted credentials for a user and invalidates any
	 * cached client so that subsequent calls pick up the new credentials.
	 *
	 * @param userId         unique user identifier
	 * @param rawCredentials plain-text Coinbase Advanced Trade credentials blob
	 */
	public void registerCredentials(String userId, String rawCredentials) {
		if (userId == null || userId.isBlank()) {
			throw new IllegalArgumentException("userId must not be null or blank");
		}
		if (rawCredentials == null || rawCredentials.isBlank()) {
			throw new IllegalArgumentException("rawCredentials must not be null or blank");
		}

		String encrypted = encryptionService.encrypt(rawCredentials);

		UserCredentials entity = credentialsRepository.findByUserId(userId)
				.orElseGet(() -> {
					UserCredentials uc = new UserCredentials();
					uc.setUserId(userId);
					uc.setCreatedAt(Instant.now());
					return uc;
				});

		entity.setEncryptedCredentials(encrypted);
		entity.setUpdatedAt(Instant.now());
		credentialsRepository.save(entity);

		clientCache.invalidate(userId);
		log.info("Credentials registered for user [{}]", userId);
	}

	/**
	 * Removes a user's credentials from the database and evicts the cached client.
	 *
	 * @param userId unique user identifier
	 */
	public void removeCredentials(String userId) {
		credentialsRepository.deleteByUserId(userId);
		clientCache.invalidate(userId);
		log.info("Credentials removed for user [{}]", userId);
	}

	/**
	 * Returns {@code true} if credentials are stored for the given user.
	 */
	public boolean hasCredentials(String userId) {
		return credentialsRepository.existsByUserId(userId);
	}

	// ------------------------------------------------------------------
	// Internal
	// ------------------------------------------------------------------

	private CoinbaseAdvancedClient buildClient(String userId) {
		UserCredentials stored = credentialsRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalArgumentException(
						"No credentials found for user: " + userId));

		String raw = encryptionService.decrypt(stored.getEncryptedCredentials());
		try {
			CoinbaseAdvancedCredentials credentials = new CoinbaseAdvancedCredentials(raw);
			CoinbaseAdvancedClient client = new CoinbaseAdvancedClient(credentials);
			log.info("Coinbase client created for user [{}]", userId);
			return client;
		} catch (Throwable t) {
			throw new RuntimeException(
					"Failed to create Coinbase client for user " + userId + ": " + t.getMessage(), t);
		}
	}
}
