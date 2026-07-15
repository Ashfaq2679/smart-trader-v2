package com.smarttrader.v2.liquidity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.constants.LiquidityConstants;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.LiquidityMap;
import com.smarttrader.v2.model.LiquidityPool;
import com.smarttrader.v2.model.PoolType;
import com.smarttrader.v2.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds and maintains the rolling Liquidity Map for a symbol, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md section 1A.1 / V2_TECH_SPEC_v2.5.md section 3.
 *
 * Phase 1A uses candle wicks as an L2 proxy (no order-book snapshots yet, per the plan);
 * fractal highs/lows on 15m and 1h candles stand in for where resting liquidity likely
 * sits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiquidityMapperService {

    private final ProductService productService;
    private final LiquidityPoolRepository repository;
    private final Cache<String, List<LiquidityPool>> cache;

    public LiquidityMap mapLiquidity(String symbol, AnalysisContext ctx) {
        List<LiquidityPool> cached = cache.getIfPresent(symbol);
        if (cached == null) {
            cached = repository.findActivePoolsBySymbol(symbol);
            cache.put(symbol, cached);
        }

        List<Candle> candles15m = productService.getLiveCandles(symbol, Granularity.FIFTEEN_MINUTE);
        List<Candle> candles1h = productService.getLiveCandles(symbol, Granularity.ONE_HOUR);

        List<LiquidityPool> fractals15m = detectFractals(symbol, candles15m, ctx.atr());
        List<LiquidityPool> fractals1h = detectFractals(symbol, candles1h, ctx.atr());

        double clusterThreshold = ctx.atr() * LiquidityConstants.EQH_EQL_ATR_THRESHOLD;
        List<LiquidityPool> eqhEql = clusterExtremes(symbol, fractals15m, fractals1h, clusterThreshold);

        List<LiquidityPool> sessionPools = detectSessionExtremes(symbol, candles1h);

        List<LiquidityPool> allPools = merge(cached, eqhEql, sessionPools, clusterThreshold);
        updateDensity(allPools, Instant.now());
        repository.saveAll(allPools);
        cache.put(symbol, allPools);

        log.info("liquidityMapper symbol={} pools={} (eqhEql={}, session={})",
                symbol, allPools.size(), eqhEql.size(), sessionPools.size());
        return new LiquidityMap(allPools, System.nanoTime());
    }

    /**
     * 2-bar left/right fractal detection: a bar is a high (low) fractal if its high (low)
     * exceeds (is below) both its immediate neighbors. Each fractal becomes a single-touch
     * candidate pool; clusterExtremes groups nearby ones into genuine EQH/EQL pools.
     */
    List<LiquidityPool> detectFractals(String symbol, List<Candle> candles, double atr) {
        List<LiquidityPool> pools = new ArrayList<>();
        for (int i = 1; i < candles.size() - 1; i++) {
            Candle prev = candles.get(i - 1);
            Candle curr = candles.get(i);
            Candle next = candles.get(i + 1);

            if (curr.high() > prev.high() && curr.high() > next.high()) {
                pools.add(candidatePool(symbol, curr.high(), PoolType.EQH, curr.timestamp()));
            }
            if (curr.low() < prev.low() && curr.low() < next.low()) {
                pools.add(candidatePool(symbol, curr.low(), PoolType.EQL, curr.timestamp()));
            }
        }
        return pools;
    }

    private LiquidityPool candidatePool(String symbol, double level, PoolType type, Instant touchedAt) {
        return LiquidityPool.builder()
                .symbol(symbol)
                .level(BigDecimal.valueOf(level))
                .type(type)
                .touches(1)
                .volume(BigDecimal.ZERO)
                .createdAtNs(toEpochNanos(touchedAt))
                .lastTouchedNs(toEpochNanos(touchedAt))
                .build();
    }

    /**
     * Groups same-type fractal candidates within `threshold` price distance of each other.
     * Per spec section 3: ">= 2 extremes within 0.15 * ATR" is what makes a level an
     * Equal High/Low, so single, unclustered fractals are dropped here.
     */
    List<LiquidityPool> clusterExtremes(String symbol, List<LiquidityPool> pools15m,
                                         List<LiquidityPool> pools1h, double threshold) {
        List<LiquidityPool> all = new ArrayList<>();
        all.addAll(pools15m);
        all.addAll(pools1h);

        List<LiquidityPool> clustered = new ArrayList<>();
        for (PoolType type : List.of(PoolType.EQH, PoolType.EQL)) {
            List<LiquidityPool> ofType = all.stream()
                    .filter(p -> p.getType() == type)
                    .sorted(Comparator.comparing(p -> p.getLevel().doubleValue()))
                    .toList();

            List<LiquidityPool> currentGroup = new ArrayList<>();
            for (LiquidityPool pool : ofType) {
                if (currentGroup.isEmpty() || withinThreshold(currentGroup, pool, threshold)) {
                    currentGroup.add(pool);
                } else {
                    addClusterIfSignificant(symbol, type, currentGroup, clustered);
                    currentGroup = new ArrayList<>(List.of(pool));
                }
            }
            addClusterIfSignificant(symbol, type, currentGroup, clustered);
        }
        return clustered;
    }

    private boolean withinThreshold(List<LiquidityPool> group, LiquidityPool candidate, double threshold) {
        double groupAverage = group.stream().mapToDouble(p -> p.getLevel().doubleValue()).average().orElse(0);
        return Math.abs(candidate.getLevel().doubleValue() - groupAverage) <= threshold;
    }

    private void addClusterIfSignificant(String symbol, PoolType type, List<LiquidityPool> group,
                                          List<LiquidityPool> output) {
        if (group.size() < 2) {
            return;
        }
        double averageLevel = group.stream().mapToDouble(p -> p.getLevel().doubleValue()).average().orElse(0);
        long earliest = group.stream().mapToLong(LiquidityPool::getCreatedAtNs).min().orElse(0);
        long latest = group.stream().mapToLong(LiquidityPool::getLastTouchedNs).max().orElse(0);

        output.add(LiquidityPool.builder()
                .symbol(symbol)
                .level(BigDecimal.valueOf(averageLevel))
                .type(type)
                .touches(group.size())
                .volume(BigDecimal.ZERO)
                .createdAtNs(earliest)
                .lastTouchedNs(latest)
                .build());
    }

    /**
     * Prior UTC day and prior UTC week high/low, from 1h candles. Finer Asia/EU/US
     * sub-session splitting (section 3) is deferred: the spec doesn't fix session hour
     * boundaries, and Phase 1A's own service outline only calls for "session extremes
     * (prior day, week, UTC-aware)" without that detail.
     */
    List<LiquidityPool> detectSessionExtremes(String symbol, List<Candle> candles1h) {
        if (candles1h.isEmpty()) {
            return List.of();
        }
        Instant now = candles1h.get(candles1h.size() - 1).timestamp();

        List<LiquidityPool> pools = new ArrayList<>();
        priorDayExtremes(symbol, candles1h, now).ifPresent(extreme -> {
            pools.add(extreme.highPool());
            pools.add(extreme.lowPool());
        });
        priorWeekExtremes(symbol, candles1h, now).ifPresent(extreme -> {
            pools.add(extreme.highPool());
            pools.add(extreme.lowPool());
        });
        return pools;
    }

    private Optional<SessionExtreme> priorDayExtremes(String symbol, List<Candle> candles, Instant now) {
        int priorDay = now.minus(1, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).getDayOfYear();
        int priorDayYear = now.minus(1, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).getYear();
        List<Candle> priorDayCandles = candles.stream()
                .filter(c -> {
                    var zdt = c.timestamp().atZone(ZoneOffset.UTC);
                    return zdt.getDayOfYear() == priorDay && zdt.getYear() == priorDayYear;
                })
                .toList();
        return sessionExtreme(symbol, priorDayCandles, now);
    }

    private Optional<SessionExtreme> priorWeekExtremes(String symbol, List<Candle> candles, Instant now) {
        // Instant only supports units up to DAYS; 1 week = 7 days.
        Instant priorWeekInstant = now.minus(7, ChronoUnit.DAYS);
        int priorWeek = priorWeekInstant.atZone(ZoneOffset.UTC).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int priorWeekYear = priorWeekInstant.atZone(ZoneOffset.UTC).get(IsoFields.WEEK_BASED_YEAR);
        List<Candle> priorWeekCandles = candles.stream()
                .filter(c -> {
                    var zdt = c.timestamp().atZone(ZoneOffset.UTC);
                    return zdt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == priorWeek
                            && zdt.get(IsoFields.WEEK_BASED_YEAR) == priorWeekYear;
                })
                .toList();
        return sessionExtreme(symbol, priorWeekCandles, now);
    }

    private Optional<SessionExtreme> sessionExtreme(String symbol, List<Candle> candles, Instant now) {
        if (candles.isEmpty()) {
            return Optional.empty();
        }
        double high = candles.stream().mapToDouble(Candle::high).max().orElseThrow();
        double low = candles.stream().mapToDouble(Candle::low).min().orElseThrow();
        long nowNs = toEpochNanos(now);

        LiquidityPool highPool = LiquidityPool.builder()
                .symbol(symbol).level(BigDecimal.valueOf(high)).type(PoolType.SESSION)
                .touches(1).volume(BigDecimal.ZERO).createdAtNs(nowNs).lastTouchedNs(nowNs).build();
        LiquidityPool lowPool = LiquidityPool.builder()
                .symbol(symbol).level(BigDecimal.valueOf(low)).type(PoolType.SESSION)
                .touches(1).volume(BigDecimal.ZERO).createdAtNs(nowNs).lastTouchedNs(nowNs).build();
        return Optional.of(new SessionExtreme(highPool, lowPool));
    }

    private record SessionExtreme(LiquidityPool highPool, LiquidityPool lowPool) {
    }

    /**
     * Combines previously-cached pools with freshly-detected ones, de-duplicating by
     * (type, level within threshold): a rediscovered pool bumps touches/lastTouchedNs on
     * the existing entry rather than creating a duplicate.
     */
    List<LiquidityPool> merge(List<LiquidityPool> cached, List<LiquidityPool> eqhEql,
                               List<LiquidityPool> sessionPools, double threshold) {
        List<LiquidityPool> merged = new ArrayList<>(cached);

        for (LiquidityPool candidate : concat(eqhEql, sessionPools)) {
            LiquidityPool existing = findMatch(merged, candidate, threshold);
            if (existing == null) {
                candidate.setId(UUID.randomUUID().toString());
                merged.add(candidate);
            } else {
                existing.setTouches(existing.getTouches() + candidate.getTouches());
                existing.setLastTouchedNs(Math.max(existing.getLastTouchedNs(), candidate.getLastTouchedNs()));
            }
        }
        return merged;
    }

    private List<LiquidityPool> concat(List<LiquidityPool> a, List<LiquidityPool> b) {
        List<LiquidityPool> result = new ArrayList<>(a);
        result.addAll(b);
        return result;
    }

    private LiquidityPool findMatch(List<LiquidityPool> existingPools, LiquidityPool candidate, double threshold) {
        return existingPools.stream()
                .filter(p -> p.getType() == candidate.getType())
                .filter(p -> Math.abs(p.getLevel().doubleValue() - candidate.getLevel().doubleValue()) <= threshold)
                .findFirst()
                .orElse(null);
    }

    /** density = touches x recency-decay x 100 / 10 (normalized to roughly 0-100). */
    void updateDensity(List<LiquidityPool> pools, Instant now) {
        for (LiquidityPool pool : pools) {
            if (pool.getExpiresAt() == null) {
                pool.setExpiresAt(now.plus(LiquidityConstants.POOL_TTL_DAYS, ChronoUnit.DAYS));
                pool.setExpiresAtNs(toEpochNanos(pool.getExpiresAt()));
            }
            long ageNs = toEpochNanos(now) - pool.getCreatedAtNs();
            double ageDays = ageNs / (24.0 * 3600 * 1_000_000_000L);
            double decay = Math.pow(LiquidityConstants.DENSITY_DECAY_LAMBDA_PER_DAY, Math.max(ageDays, 0));
            pool.setDensity((float) Math.min(100.0, pool.getTouches() * decay * 100.0 / 10.0));
        }
    }

    private static long toEpochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
