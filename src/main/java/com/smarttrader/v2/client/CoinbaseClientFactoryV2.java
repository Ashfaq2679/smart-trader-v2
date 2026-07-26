package com.smarttrader.v2.client;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds CoinbaseClient instances for the public, unauthenticated candles endpoint.
 * Kept separate from CoinbaseClientFactory (the multi-user, credential-based factory
 * that builds authenticated CoinbaseAdvancedClient/OrdersService instances) since the
 * two serve entirely different endpoints with entirely different auth - this factory
 * needs no per-user credentials at all.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CoinbaseProperties.class)
public class CoinbaseClientFactoryV2 {

    private final WebClient.Builder webClientBuilder;
    private final CoinbaseProperties properties;

    public CoinbaseClient create() {
        WebClient.Builder builder = webClientBuilder.baseUrl(properties.baseUrl());
        if (properties.bearerToken() != null && !properties.bearerToken().isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + properties.bearerToken());
        }
        return new CoinbaseClientImpl(builder.build());
    }
}
