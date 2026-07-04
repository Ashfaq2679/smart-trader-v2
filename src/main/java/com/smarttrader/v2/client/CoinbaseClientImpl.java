package com.smarttrader.v2.client;

import com.smarttrader.v2.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
class CoinbaseClientImpl implements CoinbaseClient {

    private final WebClient webClient;

    @Override
    public List<Candle> getCandles(String productId, Granularity granularity) {
        long start = System.nanoTime();
        List<Candle> candles = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/brokerage/products/{productId}/candles")
                        .queryParam("granularity", granularity.apiValue())
                        .build(productId))
                .retrieve()
                .bodyToMono(CandleResponse.class)
                .map(CoinbaseClientImpl::toCandles)
                .block();

        log.info("coinbaseClient productId={} granularity={} candles={} executionTimeMs={}",
                productId, granularity, candles == null ? 0 : candles.size(),
                (System.nanoTime() - start) / 1_000_000);

        return candles == null ? List.of() : candles;
    }

    private static List<Candle> toCandles(CandleResponse response) {
        if (response == null || response.candles() == null) {
            return List.of();
        }
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
