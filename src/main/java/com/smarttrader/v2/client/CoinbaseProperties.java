package com.smarttrader.v2.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coinbase Advanced Trade API connection settings.
 * Credentials are sourced from environment/config only, never hardcoded.
 *
 * bearerToken is sent as {@code Authorization: Bearer <token>} for the public candles
 * endpoint, per
 * https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/public/get-public-product-candles.
 *
 * keyName/privateKey are CDP Cloud API key credentials (an EC private key, PEM-encoded)
 * used for authenticated endpoints (order placement) via the official
 * com.coinbase.advanced SDK's CoinbaseAdvancedCredentials, which signs each request with
 * a fresh ES256 JWT - this codebase does not hand-roll JWT signing.
 * apiSecret is unused; retained only because it predates keyName/privateKey being added.
 */
@ConfigurationProperties(prefix = "coinbase.api")
public record CoinbaseProperties(
        String baseUrl,
        String bearerToken,
        String apiSecret,
        String keyName,
        String privateKey,
        String portfolioId
) {
    public CoinbaseProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.coinbase.com";
        }
    }

    public boolean hasOrderCredentials() {
        return keyName != null && !keyName.isBlank() && privateKey != null && !privateKey.isBlank();
    }
}
