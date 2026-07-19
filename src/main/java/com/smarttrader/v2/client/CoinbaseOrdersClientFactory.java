package com.smarttrader.v2.client;

import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.coinbase.advanced.client.CoinbaseAdvancedClient;
import com.coinbase.advanced.credentials.CoinbaseAdvancedCredentials;
import com.coinbase.advanced.orders.OrdersService;
import com.coinbase.advanced.orders.OrdersServiceImpl;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds the official Coinbase Advanced Trade SDK's OrdersService, authenticated with a
 * CDP Cloud API key (keyName + EC private key). This codebase does not hand-roll ES256
 * JWT signing: com.coinbase.advanced.credentials.CoinbaseAdvancedCredentials (from the
 * coinbase-advanced-sdk-java / coinbase-core-java dependencies already in pom.xml, used
 * by this project's pre-SMC-branch history) signs a fresh JWT per request internally.
 *
 * Separate from CoinbaseClientFactory (which builds the plain WebClient used for the
 * public, unauthenticated candles endpoint) since order placement needs different,
 * authenticated credentials entirely - CoinbaseClient/CoinbaseClientImpl stay untouched.
 *
 * Returns Optional.empty() rather than throwing when credentials aren't configured: the
 * caller (OrderService) treats "no order client available" as a fail-safe condition
 * that raises a BOLD alert, not a startup crash - this lets the rest of the application
 * (candle polling, analysis, siren) run normally with execution simply unavailable.
 */
@Slf4j
@Component
@EnableConfigurationProperties(CoinbaseProperties.class)
public class CoinbaseOrdersClientFactory {

    private final CoinbaseProperties properties;
    private volatile OrdersService cached;

    public CoinbaseOrdersClientFactory(CoinbaseProperties properties) {
        this.properties = properties;
    }

    public Optional<OrdersService> create() {
        if (!properties.hasOrderCredentials()) {
            return Optional.empty();
        }
        if (cached == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = build();
                }
            }
        }
        return Optional.of(cached);
    }

    private OrdersService build() {
        CoinbaseAdvancedCredentials credentials =
                new CoinbaseAdvancedCredentials(properties.keyName(), properties.privateKey());
        CoinbaseAdvancedClient client = new CoinbaseAdvancedClient(credentials);
        log.info("coinbaseOrdersClientFactory built OrdersService keyName={}", properties.keyName());
        return new OrdersServiceImpl(client);
    }
}
