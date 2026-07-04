package com.smarttrader.v2.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coinbase Advanced Trade API connection settings.
 * Credentials are sourced from environment/config only, never hardcoded.
 */
@ConfigurationProperties(prefix = "coinbase.api")
public record CoinbaseProperties(
        String baseUrl,
        String apiKey,
        String apiSecret
) {
    public CoinbaseProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.coinbase.com";
        }
    }
}
