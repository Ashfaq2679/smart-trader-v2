package com.smarttrader.v2.client;

import com.smarttrader.v2.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
class CoinbaseClientImpl implements CoinbaseClient {

    private final WebClient webClient;

    /** Coinbase 429s: retry a handful of times with exponential backoff before giving up. */
    private int rateLimitMaxRetries = 4;
    private Duration rateLimitMinBackoff = Duration.ofMillis(500);

    @Override
    public List<Candle> getCandles(String productId, Granularity granularity) {
        long start = System.nanoTime();
        List<Candle> candles;
        try {
            candles = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v3/brokerage/market/products/{productId}/candles")
                            .queryParam("granularity", granularity.apiValue())
                            .build(productId))
                    .retrieve()
                    .bodyToMono(CandleResponse.class)
                    .map(CoinbaseClientImpl::toCandles)
                    .retryWhen(Retry.backoff(rateLimitMaxRetries, rateLimitMinBackoff)
                            .filter(CoinbaseClientImpl::isTooManyRequests)
                            .doBeforeRetry(signal -> log.warn(
                                    "coinbaseClient productId={} granularity={} rate-limited (429), retrying attempt={}",
                                    productId, granularity, signal.totalRetries() + 1)))
                    .block();
        } catch (Exception e) {
            if (!isTooManyRequests(e) && !isTooManyRequests(e.getCause())) {
                throw e;
            }
            log.error("coinbaseClient productId={} granularity={} rate-limited (429) after {} retries, "
                            + "giving up for this poll - returning no candles",
                    productId, granularity, rateLimitMaxRetries);
            return List.of();
        }

        log.info("coinbaseClient productId={} granularity={} candles={} executionTimeMs={}",
                productId, granularity, candles == null ? 0 : candles.size(),
                (System.nanoTime() - start) / 1_000_000);

        return candles == null ? List.of() : candles;
    }

    private static boolean isTooManyRequests(Throwable throwable) {
        return throwable instanceof WebClientResponseException.TooManyRequests;
    }

    private static List<Candle> toCandles(CandleResponse response) {
        if (response == null || response.candles() == null) {
            return List.of();
        }
        System.out.println("CoinbaseClientImpl-RAW-CANDLES: " + response.candles());
        return response.candles().stream()
                .map(raw -> Candle.builder()
                        .timestamp(Instant.ofEpochSecond(Long.parseLong(raw.get("start"))))
                        .low(Double.parseDouble(raw.get("low")))
                        .high(Double.parseDouble(raw.get("high")))
                        .open(Double.parseDouble(raw.get("open")))
                        .close(Double.parseDouble(raw.get("close")))
                        .volume(Double.parseDouble(raw.get("volume")))
                        .build())
                .toList();
    }

    private record CandleResponse(List<Map<String, String>> candles) {
    }
}

