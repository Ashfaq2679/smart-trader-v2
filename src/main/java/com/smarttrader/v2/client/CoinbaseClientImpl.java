package com.smarttrader.v2.client;

import com.coinbase.advanced.model.products.GetProductCandlesResponse;
import com.smarttrader.v2.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Read-only client for the public Coinbase candle endpoint. Wired to the
 * coinbaseWebClient bean from CoinbaseWebClientConfig, distinct from the
 * per-user authenticated clients CoinbaseClientFactory builds for trading.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoinbaseClientImpl implements CoinbaseClient {

    private final WebClient webClient;

    @Override
    public List<Candle> getCandles(String productId, Granularity granularity) {
        return fetch(productId, granularity, null);
    }

    @Override
    public List<Candle> getCandles(String productId, Granularity granularity, Instant start) {
        return fetch(productId, granularity, start);
    }

    private List<Candle> fetch(String productId, Granularity granularity, Instant start) {
        long began = System.nanoTime();
        List<Candle> candles = webClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, productId, granularity, start))
                .retrieve()
                .bodyToMono(GetProductCandlesResponse.class)
                .map(CoinbaseClientImpl::toCandles)
                .block();

        log.info("coinbaseClient productId={} granularity={} start={} candles={} executionTimeMs={}",
                productId, granularity, start, candles == null ? 0 : candles.size(),
                (System.nanoTime() - began) / 1_000_000);

        return candles == null ? List.of() : candles;
    }

    private static URI buildUri(UriBuilder uriBuilder, String productId, Granularity granularity, Instant start) {
        uriBuilder.path("/api/v3/brokerage/market/products/{productId}/candles")
                .queryParam("granularity", granularity.apiValue());
        if (start != null) {
            uriBuilder.queryParam("start", start.getEpochSecond())
                    .queryParam("end", Instant.now().getEpochSecond());
        }
        return uriBuilder.build(productId);
    }

    private static List<Candle> toCandles(GetProductCandlesResponse response) {
        if (response == null || response.getCandles() == null) {
            return List.of();
        }
        return response.getCandles().stream()
                .map(raw -> Candle.builder()
                        .timestamp(Instant.ofEpochSecond(Long.parseLong(raw.getStart())))
                        .low(Double.parseDouble(raw.getLow()))
                        .high(Double.parseDouble(raw.getHigh()))
                        .open(Double.parseDouble(raw.getOpen()))
                        .close(Double.parseDouble(raw.getClose()))
                        .volume(Double.parseDouble(raw.getVolume()))
                        .build())
                .toList();
    }
}
