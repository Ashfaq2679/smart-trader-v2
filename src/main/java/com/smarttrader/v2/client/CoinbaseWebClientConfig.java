package com.smarttrader.v2.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient for the public Coinbase market-data endpoints (candles), which are read-only
 * and unauthenticated by product. This is separate from CoinbaseClientFactory, which builds
 * per-user authenticated CoinbaseAdvancedClient instances for trading operations.
 */
@Configuration
@EnableConfigurationProperties(CoinbaseProperties.class)
public class CoinbaseWebClientConfig {

    @Bean
    public WebClient coinbaseWebClient(WebClient.Builder webClientBuilder, CoinbaseProperties properties) {
        WebClient.Builder builder = webClientBuilder.baseUrl(properties.baseUrl());
        if (properties.bearerToken() != null && !properties.bearerToken().isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + properties.bearerToken());
        }
        return builder.build();
    }
}
