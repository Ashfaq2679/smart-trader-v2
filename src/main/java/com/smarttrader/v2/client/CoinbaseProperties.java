package com.smarttrader.v2.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coinbase Advanced Trade API connection settings.
 * Credentials are sourced from environment/config only, never hardcoded.
 *
 * bearerToken is sent as {@code Authorization: Bearer <token>}, per
 * https://docs.cdp.coinbase.com/api-reference/advanced-trade-api/rest-api/public/get-public-product-candles.
 * apiSecret is retained for future JWT (ES256) request signing on authenticated
 * endpoints; it is not used by the public candles endpoint.
 */
@ConfigurationProperties(prefix = "coinbase.api")
public record CoinbaseProperties(
        String baseUrl,
        String bearerToken,
        String apiSecret
) {
    public CoinbaseProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.coinbase.com";
        }
    }
}
