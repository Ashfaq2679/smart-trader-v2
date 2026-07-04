package com.smarttrader.v2.client;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds CoinbaseClient instances. Centralizing construction here keeps credential
 * handling and WebClient configuration in one place, per "constructor injection only,
 * no field injection" and "never hardcode credentials".
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CoinbaseProperties.class)
public class CoinbaseClientFactory {

    private final WebClient.Builder webClientBuilder;
    private final CoinbaseProperties properties;

    public CoinbaseClient create() {
        WebClient.Builder builder = webClientBuilder.baseUrl(properties.baseUrl());
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder = builder.defaultHeader("CB-ACCESS-KEY", properties.apiKey());
        }
        return new CoinbaseClientImpl(builder.build());
    }
}
