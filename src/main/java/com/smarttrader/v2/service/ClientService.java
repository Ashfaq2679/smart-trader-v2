package com.smarttrader.v2.service;

import org.springframework.stereotype.Service;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.smarttrader.v2.client.CoinbaseClientFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Convenience façade that delegates to {@link CoinbaseClientFactory} for
 * per-user Coinbase client retrieval.
 *
 * <p><b>Design Pattern: Delegation / Façade</b> — Provides a simple API for
 * obtaining an authenticated {@link CoinbaseAdvancedClient} for a specific user.
 * All credential storage, encryption, caching, and client construction are
 * handled by the underlying {@link CoinbaseClientFactory}.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClientService {

	private final CoinbaseClientFactory coinbaseClientFactory;

	/**
	 * Returns a {@link CoinbaseAdvancedClient} for the specified user.
	 *
	 * @param userId unique user identifier
	 * @return a ready-to-use Coinbase client bound to the user's credentials
	 * @throws IllegalArgumentException if no credentials are stored for the user
	 */
	public CoinbaseAdvancedClient getClientForUser(String userId) {
		return coinbaseClientFactory.getClientForUser(userId);
	}

	/**
	 * Registers (or updates) Coinbase credentials for a user.
	 *
	 * @param userId         unique user identifier
	 * @param rawCredentials plain-text Coinbase Advanced Trade credential blob
	 */
	public void registerCredentials(String userId, String rawCredentials) {
		coinbaseClientFactory.registerCredentials(userId, rawCredentials);
	}

	/**
	 * Removes the stored credentials for a user.
	 *
	 * @param userId unique user identifier
	 */
	public void removeCredentials(String userId) {
		coinbaseClientFactory.removeCredentials(userId);
	}

	/**
	 * Returns {@code true} if credentials are stored for the given user.
	 */
	public boolean hasCredentials(String userId) {
		return coinbaseClientFactory.hasCredentials(userId);
	}
	
	public CoinbaseAdvancedClient getCoinbaseClientForUserFromCache(String userId) {
		return coinbaseClientFactory.getCoinbaseClientForUserFromCache(userId);
	}
}
