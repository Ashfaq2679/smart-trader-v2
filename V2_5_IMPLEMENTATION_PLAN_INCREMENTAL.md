# V2.5 IMPLEMENTATION PLAN — INCREMENTAL BUILD ON v2.2 FOUNDATION

## EXECUTIVE SUMMARY

Smart Trader v2.2 already has:
- ✅ OAuth/exchange auth + CoinbaseClient (exchange connection ready)
- ✅ Candle ingestion (ProductService, all timeframes)
- ✅ Core models (AnalysisContext, SignalResult, TradeDecision)
- ✅ Market regime detection (5 regimes: BREAKOUT, CONTINUATION, PULLBACK, PANIC, DISTRIBUTION)
- ✅ 3 production strategies (Breakout, Continuation, Pullback)
- ✅ Risk engine + position sizing (R:R filtering, 1% risk per trade)
- ✅ MongoDB + Caffeine cache + Spring Boot 3.3.4
- ✅ Testing infrastructure (JUnit5, Mockito)

**This plan adds v2.5 features incrementally**, reusing all existing patterns. No rewrites. Estimate: **6–8 weeks, 2 FTE**.

---

## PHASE 0: EXTEND FOUNDATION (1 week, LOW RISK)

Extend existing code to support v2.5 data feeds. **No breaking changes to v2.2.**

### 0.1 Extend AnalysisContext with v2.5 Fields

**File:** `src/main/java/com/smarttrader/v2/model/AnalysisContext.java`

Add new fields (keep existing ones):
```java
@Builder
public record AnalysisContext(
    // Existing v2.2 fields
    double price,
    double ema9,
    double ema21,
    double ema50,
    double atr,
    TrendDirection trendDirection,
    double trendStrength,
    double nearestSupport,
    double nearestResistance,
    boolean volumeSpike,
    boolean strongCandle,
    boolean isAboveEMA,
    boolean recentBreakout,
    boolean atrSpike,
    double consolidationRangePercent,
    
    // NEW v2.5 fields
    double cvd1m,                    // Cumulative Volume Delta at 1m bar close
    double cvdSlope5m,               // CVD slope over 20 × 5m bars (linear regression)
    boolean cvdDivergence,           // price new high but CVD didn't
    
    double fundingRateBps,           // Perpetual funding in basis points
    int fundingPercentile30d,        // 0-100, crowd positioning proxy
    double oiChange1h,               // OI % change last hour
    double oiChange24h,              // OI % change last 24h
    boolean oiConfirmsUp,            // price up AND OI up
    boolean oiConfirmsDown,          // price down AND OI down
    
    LiquidityMap liquidityMap,       // pools, sweeps, densities
    double vwapSession,              // session VWAP (UTC)
    double vwapSession1SigmaUpper,   // +1σ band
    double vwapSession1SigmaLower,   // -1σ band
    
    boolean cascadeActive,           // liquidation cascade detected
    List<OpportunitySweep> recentSweeps // last 5 swept pools (for SweepReclaim)
) {}
```

**Impact:** Backward compatible. Existing AnalysisContext.Builder calls still work (new fields default to null/false/0).

### 0.2 Extend MarketRegime Enum

**File:** `src/main/java/com/smarttrader/v2/model/MarketRegime.java`

```java
public enum MarketRegime {
    // Existing v2.2
    BREAKOUT,
    CONTINUATION,
    PULLBACK,
    PANIC,
    DISTRIBUTION,
    
    // NEW v2.5
    RANGE,           // bands wide, no trend, ADX < 25
    CHOP,            // toxic (spread > 8bps OR band < 1.5 × ATR)
    NEWS_SHOCK,      // market alarm / flash-crash
    SQUEEZE_LONG,    // funding > 90th percentile AND OI ↑ 15% (retail max-long)
    SQUEEZE_SHORT    // funding < 10th percentile AND OI ↑ 15% (retail max-short)
}
```

**Migration:** MarketRegimeDetector.detect() returns v2.2 regime for now; Phase 1B extends it.

### 0.3 New Model Classes (Minimal, Phase 0 only)

**File:** `src/main/java/com/smarttrader/v2/model/LiquidityPool.java`
```java
@Data
@Builder
public class LiquidityPool {
    String id;                              // MongoDB ObjectId
    String symbol;
    BigDecimal level;
    PoolType type;                          // EQH, EQL, SESSION, ROUND
    float density;                          // 0-100
    int touches;
    BigDecimal volume;
    long lastTouchedNs;
    long createdAtNs;
    long expiresAtNs;                       // TTL: 5 days
}

public enum PoolType { EQH, EQL, SESSION, ROUND }
```

**File:** `src/main/java/com/smarttrader/v2/model/OpportunitySweep.java`
```java
@Data
@Builder
public class OpportunitySweep {
    String symbol;
    BigDecimal level;
    String side;                            // "UP" or "DOWN"
    float density;
    boolean reclaimed;                      // closed back inside within 2 bars
    long detectedAtNs;
}
```

### 0.4 Spring Events Infrastructure (Non-Breaking)

**File:** `src/main/java/com/smarttrader/v2/event/TradingEvent.java`
```java
public abstract class TradingEvent {
    String eventId;           // UUID
    String eventType;         // e.g., "liquidity.SweepDetected"
    long timestampNs;         // UTC nanoseconds
    String symbol;
    int schemaVersion;        // for replay
}
```

**File:** `src/main/java/com/smarttrader/v2/event/LiquiditySweepDetectedEvent.java`
```java
public class LiquiditySweepDetectedEvent extends TradingEvent {
    BigDecimal level;
    String side;
    float density;
    boolean reclaimed;
}
```

Same pattern for: `OpportunitySirenEvent`, `AbsorptionDetectedEvent`, `CascadeStateChangedEvent`, etc.

**File:** `src/main/java/com/smarttrader/v2/event/TradingEventPublisher.java`
```java
@Component
public class TradingEventPublisher {
    @Autowired
    private ApplicationEventPublisher publisher;
    
    public void publish(TradingEvent event) {
        publisher.publishEvent(event);
    }
}
```

### 0.5 MongoDB Schema Extensions

Add TTL indexes to MongoDB configuration:
```java
@Configuration
public class MongoDbConfig {
    @Bean
    public IndexResolver mongoIndexResolver() {
        return new MongoPersistentEntityIndexResolver(mongoMappingContext);
    }
}
```

Collections to create (with TTL indexes):
- `liquidity_pools` (TTL: 5 days)
- `opportunities` (TTL: 180 days)
- `strategy_states` (no TTL, state tracking)
- `trades` (TTL: 90 days)
- `config_snapshots` (no TTL, audit trail)
- `feed_health` (TTL: 30 days)

### 0.6 Configuration & Properties

**File:** `src/main/resources/application.yml`
```yaml
smart-trader:
  v2_5:
    liquidity:
      ttl-days: 5
      eqh-eql-threshold: 0.15  # 0.15 × ATR
    crowd-positioning:
      funding-percentile-crowded-long: 90
      funding-percentile-crowded-short: 10
      oi-change-threshold: 0.15
    cascade:
      bar-range-multiplier: 3
      oi-change-threshold: -0.05
```

### 0.7 Testing Gates for Phase 0

- [ ] AnalysisContext extends cleanly without breaking existing v2.2 tests
- [ ] MarketRegime enum compiles with new entries
- [ ] Spring Events infrastructure loads (ApplicationEventPublisher wiring)
- [ ] MongoDB TTL index configuration verified
- [ ] All v2.2 existing tests still pass (regression)

**Go/No-Go:** All v2.2 functionality preserved. New fields/enums in place but not yet consumed.

---

## PHASE 1A: LIQUIDITY MAP SERVICE (2 weeks)

Build on top of existing CandleBufferService and ProductService.

### 1A.1 LiquidityMapperService

**File:** `src/main/java/com/smarttrader/v2/liquidity/LiquidityMapperService.java`

Inputs:
- ProductService (live candles, all granularities already available)
- L2 snapshots (optional, new data source; Phase 1A uses candle wicks as proxy)
- AnalysisContext.atr (from v2.2)

Outputs:
- LiquidityPool documents to MongoDB
- Caffeine cache (1-hour TTL per symbol)

Logic:
```java
@Service
@RequiredArgsConstructor
public class LiquidityMapperService {
    private final ProductService productService;
    private final LiquidityPoolRepository repository;
    private final Cache<String, List<LiquidityPool>> cache;  // Caffeine
    
    public LiquidityMap mapLiquidity(String symbol, AnalysisContext ctx) {
        // 1. Load cached pools if recent; else rebuild from DB
        List<LiquidityPool> pools = cache.getIfPresent(symbol);
        if (pools == null) {
            pools = repository.findActivePoolsBySymbol(symbol);
            cache.put(symbol, pools);
        }
        
        // 2. Update fractals (15m, 1h candles from ProductService)
        List<Candle> candles15m = productService.getLiveCandles(symbol, Granularity.FIFTEEN_MINUTES);
        List<Candle> candles1h = productService.getLiveCandles(symbol, Granularity.ONE_HOUR);
        
        List<LiquidityPool> fractals15m = detectFractals(candles15m, ctx.atr(), 15);
        List<LiquidityPool> fractals1h = detectFractals(candles1h, ctx.atr(), 60);
        
        // 3. Cluster into EQH/EQL
        List<LiquidityPool> eqhEql = clusterExtremes(fractals15m, fractals1h, ctx.atr() * 0.15);
        
        // 4. Add session extremes (prior day, week, UTC-aware)
        List<LiquidityPool> sessionPools = detectSessionExtremes(symbol);
        
        // 5. Merge, score density, persist
        List<LiquidityPool> allPools = merge(pools, fractals15m, fractals1h, eqhEql, sessionPools);
        updateDensity(allPools, ctx.atr());
        repository.saveAll(allPools);
        
        return new LiquidityMap(allPools, System.nanoTime());
    }
    
    private List<LiquidityPool> detectFractals(List<Candle> candles, double atr, int timeframeMinutes) {
        // 2-bar left/right high/low detection
        List<LiquidityPool> pools = new ArrayList<>();
        for (int i = 1; i < candles.size() - 1; i++) {
            Candle prev = candles.get(i - 1);
            Candle curr = candles.get(i);
            Candle next = candles.get(i + 1);
            
            // High fractal: curr.high > prev.high AND curr.high > next.high
            if (curr.high() > prev.high() && curr.high() > next.high()) {
                pools.add(LiquidityPool.builder()
                    .level(BigDecimal.valueOf(curr.high()))
                    .type(PoolType.EQH)
                    .touches(1)
                    .createdAtNs(curr.closeTimeNs())
                    .build());
            }
            // Low fractal: curr.low < prev.low AND curr.low < next.low
            if (curr.low() < prev.low() && curr.low() < next.low()) {
                pools.add(LiquidityPool.builder()
                    .level(BigDecimal.valueOf(curr.low()))
                    .type(PoolType.EQL)
                    .touches(1)
                    .createdAtNs(curr.closeTimeNs())
                    .build());
            }
        }
        return pools;
    }
    
    private List<LiquidityPool> clusterExtremes(List<LiquidityPool> pools15m, List<LiquidityPool> pools1h, double threshold) {
        // Group pools within threshold, count touches, weight by recency
        // ... clustering logic
        return new ArrayList<>();  // stub
    }
    
    private void updateDensity(List<LiquidityPool> pools, double atr) {
        // density = touches × (1 - 0.8^daysSince)
        long nowNs = System.nanoTime();
        for (LiquidityPool pool : pools) {
            long ageNs = nowNs - pool.createdAtNs();
            double ageDays = ageNs / (24.0 * 3600 * 1e9);
            double decay = Math.pow(0.8, ageDays);
            pool.setDensity((float)(pool.touches() * decay * 100.0 / 10.0));  // normalize to 0-100
        }
    }
}
```

### 1A.2 SweepDetectorService

**File:** `src/main/java/com/smarttrader/v2/liquidity/SweepDetectorService.java`

Listens to candle-close events; detects sweeps:
```java
@Service
@RequiredArgsConstructor
public class SweepDetectorService {
    private final LiquidityMapperService liquidityMapper;
    private final TradingEventPublisher eventPublisher;
    
    public void onCandleClose(String symbol, Candle candle, AnalysisContext ctx) {
        LiquidityMap map = liquidityMapper.mapLiquidity(symbol, ctx);
        
        for (LiquidityPool pool : map.pools()) {
            // Check: did price trade beyond pool, then close back inside within 2 bars?
            if (isSweep(candle, pool)) {
                OpportunitySweep sweep = OpportunitySweep.builder()
                    .symbol(symbol)
                    .level(pool.level())
                    .side(candle.close() > pool.level() ? "UP" : "DOWN")
                    .density(pool.density())
                    .reclaimed(true)
                    .detectedAtNs(System.nanoTime())
                    .build();
                
                LiquiditySweepDetectedEvent event = new LiquiditySweepDetectedEvent();
                event.symbol = symbol;
                event.level = pool.level();
                event.density = pool.density();
                eventPublisher.publish(event);
            }
        }
    }
    
    private boolean isSweep(Candle candle, LiquidityPool pool) {
        // Stub: check wick beyond, close inside
        return candle.low() < pool.level() && candle.close() > pool.level();
    }
}
```

### 1A.3 Integration with AnalysisContextBuilder

**File:** `src/main/java/com/smarttrader/v2/context/AnalysisContextBuilder.java` (NEW)

```java
@Service
@RequiredArgsConstructor
public class AnalysisContextBuilder {
    private final LiquidityMapperService liquidityMapper;
    private final ProductService productService;
    
    public AnalysisContext build(String symbol, Granularity granularity) {
        // Existing v2.2 context building (via indicators, EMAs, etc.)
        AnalysisContext v22Ctx = buildV22Context(symbol, granularity);
        
        // Extend with v2.5 fields
        List<Candle> candles1m = productService.getLiveCandles(symbol, Granularity.ONE_MINUTE);
        double cvd = calculateCVD(candles1m);
        
        LiquidityMap liquidityMap = liquidityMapper.mapLiquidity(symbol, v22Ctx);
        
        return v22Ctx.toBuilder()
            .cvd1m(cvd)
            .liquidityMap(liquidityMap)
            // ... other v2.5 fields
            .build();
    }
    
    private double calculateCVD(List<Candle> candles) {
        // Stub: sum of (buy-taker - sell-taker) volumes from matches stream
        // Phase 1B: integrate with actual matches ingestion
        return 0.0;
    }
}
```

### 1A.4 LiquidityPoolRepository

**File:** `src/main/java/com/smarttrader/v2/liquidity/LiquidityPoolRepository.java`

```java
@Repository
public interface LiquidityPoolRepository extends MongoRepository<LiquidityPool, String> {
    List<LiquidityPool> findActivePoolsBySymbol(String symbol);
    void deleteExpiredPools();  // TTL index handles this, but query support
}
```

### 1A.5 Testing Gates

- [ ] Fractal detection: manual labeling of 50 candles, ≥ 95% accuracy
- [ ] Density decay formula: 0.8^days produces expected values
- [ ] MongoDB persistence: pools persist and expire correctly (TTL index)
- [ ] Caffeine cache: 1-hour TTL, eviction works
- [ ] Sweep detection: catch 3 known whale sweeps from replay data
- [ ] AnalysisContextBuilder extends v2.2 context cleanly

**Go/No-Go:** Liquidity map accuracy ≥ 95%, sweep detection recall ≥ 90%.

---

## PHASE 1B: CROWD POSITIONING (1.5 weeks, PARALLEL with 1A.2 end)

### 1B.1 CVDCalculatorService

Ingests from matches stream (will be added to CoinbaseClientImpl via WebSocket).

**File:** `src/main/java/com/smarttrader/v2/positioning/CVDCalculatorService.java`

```java
@Service
@RequiredArgsConstructor
public class CVDCalculatorService {
    private final Map<String, Double> cvdBySymbol = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> cvdHistory = new ConcurrentHashMap<>();
    
    public void onMatchesStream(String symbol, List<Match> matches) {
        // Sum: buy-taker volume - sell-taker volume
        double deltaCVD = matches.stream()
            .mapToDouble(m -> m.takerSide().equals("BUY") ? m.volume() : -m.volume())
            .sum();
        
        double currentCVD = cvdBySymbol.getOrDefault(symbol, 0.0) + deltaCVD;
        cvdBySymbol.put(symbol, currentCVD);
        
        // Maintain rolling 100-bar history for slope calculation
        cvdHistory.computeIfAbsent(symbol, k -> new ArrayList<>()).add(currentCVD);
    }
    
    public double getCVD1m(String symbol) {
        return cvdBySymbol.getOrDefault(symbol, 0.0);
    }
    
    public double getCVDSlope5m(String symbol) {
        List<Double> history = cvdHistory.get(symbol);
        if (history == null || history.size() < 20) return 0.0;
        
        // Linear regression on last 100 values (or fewer if not available)
        int n = Math.min(100, history.size());
        List<Double> tail = history.subList(history.size() - n, history.size());
        return linearRegression(tail);
    }
    
    private double linearRegression(List<Double> values) {
        // Stub: return slope of linear fit
        return 0.0;
    }
}
```

### 1B.2 FundingMonitorService

Periodic polling of external perpetual funding API (config-selected: Binance, Bybit, Coinbase Perps).

**File:** `src/main/java/com/smarttrader/v2/positioning/FundingMonitorService.java`

```java
@Service
@RequiredArgsConstructor
public class FundingMonitorService {
    
    @Scheduled(fixedRate = 28800000)  // 8 hours
    public void fetchFunding() {
        String venue = "binance";  // config
        for (String symbol : trackedSymbols) {
            double fundingRate = fetchFromVenue(venue, symbol);
            storeFunding(symbol, fundingRate);
        }
    }
    
    public int getFundingPercentile30d(String symbol) {
        // Compute 30-day percentile, cache for 1h
        List<Double> rates = loadFunding30d(symbol);
        double currentRate = rates.get(rates.size() - 1);
        return (int)(percentile(rates, currentRate) * 100);  // 0-100
    }
    
    private double fetchFromVenue(String venue, String symbol) {
        // Call Binance/Bybit API; return basis points
        return 0.0;  // stub
    }
}
```

### 1B.3 OIMonitorService

Similar pattern to FundingMonitor; fetch Open Interest changes.

```java
@Service
@RequiredArgsConstructor
public class OIMonitorService {
    
    @Scheduled(fixedRate = 3600000)  // 1 hour
    public void fetchOI() {
        // Fetch OI for all tracked symbols, compute 1h/24h % change
    }
    
    public double getOIChange1h(String symbol) {
        // (currentOI - oiFrom1hAgo) / oiFrom1hAgo * 100
        return 0.0;  // stub
    }
}
```

### 1B.4 AbsorptionDetectorService

Detects big buyer accumulation: large sell-taker volume with minimal price drop.

```java
@Service
@RequiredArgsConstructor
public class AbsorptionDetectorService {
    private final CVDCalculatorService cvdCalculator;
    private final TradingEventPublisher eventPublisher;
    
    public void onCandleClose(String symbol, Candle candle, AnalysisContext ctx) {
        double relativeSellVolume = candle.volume() * (1.0 - candle.close() / candle.open());  // rough proxy
        double priceDecline = (candle.open() - candle.close()) / ctx.atr();
        
        // Absorption: 2.5× relative volume sell + < 0.25 × ATR price decline
        if (relativeSellVolume > 2.5 && priceDecline < 0.25) {
            AbsorptionDetectedEvent event = new AbsorptionDetectedEvent();
            event.symbol = symbol;
            event.side = "BID";
            eventPublisher.publish(event);
        }
    }
}
```

### 1B.5 Integration with AnalysisContextBuilder

Extend AnalysisContextBuilder to populate CVD, funding, OI fields.

### 1B.6 Testing Gates

- [ ] CVD calculation: 10,000 synthetic matches, manual verification
- [ ] CVD divergence detection: catches known divergences
- [ ] Funding API integration: fetches without blocking candles
- [ ] OI confirmation matrix: price + OI logic correct for all 4 cases
- [ ] Absorption detection: catches 3 known accumulation events
- [ ] Degradation: funding API down → fields null, no system halt

**Go/No-Go:** All feeds reach AnalysisContext with ≤ 10ms latency.

---

## PHASE 2: NEW STRATEGIES (2 weeks, PARALLEL with 1B end)

Implement new strategies as Spring beans implementing `TradingStrategy`.

### 2.1 SweepReclaimStrategy

**File:** `src/main/java/com/smarttrader/v2/strategy/SweepReclaimStrategy.java`

```java
@Component
@RequiredArgsConstructor
public class SweepReclaimStrategy implements TradingStrategy {
    
    private final TradingEventPublisher eventPublisher;
    
    @EventListener
    public void onSweepDetected(LiquiditySweepDetectedEvent event) {
        // Trade **against** the sweep
        // If sweep below EQL → long entry
        // If sweep above EQH → short entry
    }
    
    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        // Check for recent sweeps in AnalysisContext.recentSweeps
        if (ctx.recentSweeps().isEmpty()) {
            return SignalResult.invalid("no recent sweeps");
        }
        
        OpportunitySweep sweep = ctx.recentSweeps().get(0);
        
        // Confluence required: pool density ≥ 50, reclaim volume ≥ 1.5×
        if (sweep.density() < 50) {
            return SignalResult.invalid("pool density too low");
        }
        
        // Entry: market on reclaim close
        // SL: 0.35 × ATR beyond sweep extreme
        // TP1: opposite side of local range
        
        double entry = ctx.price();
        double sl = sweep.side().equals("UP")
            ? entry - (ctx.atr() * 0.35)
            : entry + (ctx.atr() * 0.35);
        double tp = entry + (ctx.atr() * 1.5);  // min 1.5R
        
        return SignalResult.builder()
            .valid(true)
            .strategyName("SweepReclaim")
            .entry(entry)
            .stop(sl)
            .target(tp)
            .riskReward((tp - entry) / Math.abs(entry - sl))
            .build();
    }
    
    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.RANGE, MarketRegime.BREAKOUT);
    }
}
```

### 2.2 SFPReversalStrategy

**File:** `src/main/java/com/smarttrader/v2/strategy/SFPReversalStrategy.java`

Swing Failure Pattern: wick beyond prior extreme, close back inside.

```java
@Component
public class SFPReversalStrategy implements TradingStrategy {
    
    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        // Detect: current candle wick beyond prior fractal, close back inside
        // Confluence required: CVD divergence OR absorption OR funding extreme
        
        // Stub implementation
        return SignalResult.invalid("SFP not yet fully implemented");
    }
    
    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.PULLBACK, MarketRegime.RANGE);
    }
}
```

### 2.3 RangeHarvesterStrategy

**File:** `src/main/java/com/smarttrader/v2/strategy/RangeHarvesterStrategy.java`

Buy the dip at range low, sell at range high.

```java
@Component
public class RangeHarvesterStrategy implements TradingStrategy {
    
    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        // Preconditions: RANGE regime, band width ≥ 2 × ATR
        // Entry: sweep or wick + absorption at range low
        // Max 2 round-trips/side/session
        
        return SignalResult.invalid("Range Harvester not yet implemented");
    }
    
    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.RANGE);
    }
}
```

### 2.4 AntiSMCStrategy

Front-run SMC Order Block fills, fade failures.

```java
@Component
public class AntiSMCStrategy implements TradingStrategy {
    
    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        // Detect: Order Block (opposite-color 15m candle before impulse ≥ 2 × ATR)
        // Detect: FVG (3-bar gap ≥ 0.5 × ATR)
        // Entry: 0.1 × ATR before OB/FVG near edge
        
        return SignalResult.invalid("Anti-SMC not yet implemented");
    }
    
    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.BREAKOUT, MarketRegime.CONTINUATION);
    }
}
```

### 2.5 ShortSideStrategy & CascadeReversalStrategy

Short detection and cascade playbook.

```java
@Component
public class ShortSideStrategy implements TradingStrategy {
    
    @Override
    public SignalResult evaluate(AnalysisContext ctx) {
        // TREND_DOWN + oiConfirmsDown + CVD 20-bar low
        // 1h close below prior week low → CRITICAL severity
        
        return SignalResult.invalid("Short-Side not yet implemented");
    }
    
    @Override
    public Set<MarketRegime> applicableRegimes() {
        return Set.of(MarketRegime.PANIC, MarketRegime.PULLBACK);
    }
}
```

### 2.6 Extend StrategySelector

**File:** `src/main/java/com/smarttrader/v2/strategy/StrategySelector.java` (EXISTING, EXTEND)

Current logic: map MarketRegime → single strategy. Extend to:
1. Support new v2.5 regimes (RANGE, CHOP, NEWS_SHOCK, SQUEEZE_LONG/SHORT)
2. Return **primary + secondary strategy pair** per Playbook Matrix (§2 spec)

```java
@Component
@RequiredArgsConstructor
public class StrategySelector {
    
    private final BreakoutStrategy breakoutStrategy;
    private final ContinuationStrategy continuationStrategy;
    private final PullbackStrategy pullbackStrategy;
    private final SweepReclaimStrategy sweepReclaimStrategy;
    private final SFPReversalStrategy sfpReversalStrategy;
    private final RangeHarvesterStrategy rangeHarvesterStrategy;
    // ... other strategies
    
    public List<TradingStrategy> selectStrategies(MarketRegime regime) {
        return switch (regime) {
            case BREAKOUT -> List.of(breakoutStrategy, sweepReclaimStrategy);
            case CONTINUATION -> List.of(continuationStrategy, sfpReversalStrategy);
            case PULLBACK -> List.of(pullbackStrategy, rangeHarvesterStrategy);
            case RANGE -> List.of(rangeHarvesterStrategy, sfpReversalStrategy);
            case SQUEEZE_LONG -> List.of(
                new DefensiveStrategy(),  // Cancel longs, prepare shorts
                sweepReclaimStrategy);
            case SQUEEZE_SHORT -> List.of(
                new DefensiveStrategy(),  // Prepare longs, tighten trails
                rangeHarvesterStrategy);
            case CHOP -> List.of();  // NO_TRADE
            case NEWS_SHOCK -> List.of();  // 3-bar observation, then evaluate
            case PANIC -> List.of(pullbackStrategy, sfpReversalStrategy);
            default -> List.of();
        };
    }
}
```

### 2.7 Extend MarketRegimeDetector

**File:** `src/main/java/com/smarttrader/v2/regime/MarketRegimeDetector.java` (EXISTING, EXTEND)

Add detection for new regimes using new AnalysisContext fields:

```java
@Component
public class MarketRegimeDetector {
    
    public MarketRegime detect(AnalysisContext ctx) {
        // New v2.5 checks FIRST (highest priority)
        if (isSqueezeLong(ctx)) return MarketRegime.SQUEEZE_LONG;
        if (isSqueezeShort(ctx)) return MarketRegime.SQUEEZE_SHORT;
        if (isNewsShock(ctx)) return MarketRegime.NEWS_SHOCK;
        if (isChop(ctx)) return MarketRegime.CHOP;
        if (isRange(ctx)) return MarketRegime.RANGE;
        
        // Fall back to v2.2 detection
        if (isBreakout(ctx)) return MarketRegime.BREAKOUT;
        if (isContinuation(ctx)) return MarketRegime.CONTINUATION;
        if (isPullback(ctx)) return MarketRegime.PULLBACK;
        if (isPanic(ctx)) return MarketRegime.PANIC;
        
        return MarketRegime.DISTRIBUTION;
    }
    
    private boolean isSqueezeLong(AnalysisContext ctx) {
        return ctx.fundingPercentile30d() >= 90
            && ctx.oiChange24h() > 0.15;
    }
    
    private boolean isRange(AnalysisContext ctx) {
        return ctx.trendStrength() < 0.25
            && !ctx.atrSpike();
    }
    
    private boolean isChop(AnalysisContext ctx) {
        // Placeholder: would need L2 spread data
        return false;
    }
    
    // ... other new regime detectors
}
```

### 2.8 Testing Gates

- [ ] Each new strategy backtests with expectancy > 0 (≥ 500 trades)
- [ ] Parameter sensitivity: ±20% on thresholds doesn't flip sign
- [ ] StrategySelector routes to correct primary + secondary per regime
- [ ] MarketRegimeDetector detects all 10 regimes correctly
- [ ] All v2.2 strategy tests still pass (regression)

**Go/No-Go:** All new strategies pass backtest. Playbook matrix routing verified.

---

## PHASE 3: OPPORTUNITY SIREN & EVENT SYSTEM (1.5 weeks)

### 3.1 OpportunitySirenService

**File:** `src/main/java/com/smarttrader/v2/siren/OpportunitySirenService.java`

```java
@Service
@RequiredArgsConstructor
public class OpportunitySirenService {
    
    private final TradingEventPublisher eventPublisher;
    private final OpportunityRepository opportunityRepository;
    
    public void onSignalEvaluated(String symbol, TradingStrategy strategy, SignalResult signal, AnalysisContext ctx) {
        // Every signal becomes an opportunity (executable or not)
        Opportunity opp = Opportunity.builder()
            .symbol(symbol)
            .playbook(strategy.getClass().getSimpleName())
            .direction(signal.direction())
            .severity(calculateSeverity(ctx, signal))
            .executable(true)  // TODO: check venue capability
            .reason(signal.reason())
            .contextSnapshot(ctx)  // serialize full context
            .createdAtNs(System.nanoTime())
            .build();
        
        opportunityRepository.save(opp);
        
        OpportunitySirenEvent event = new OpportunitySirenEvent();
        event.symbol = symbol;
        event.severity = opp.severity();
        eventPublisher.publish(event);
    }
    
    private Severity calculateSeverity(AnalysisContext ctx, SignalResult signal) {
        // CRITICAL: regime breakdown, SQUEEZE entry, major liquidation
        // HIGH: counter-crowd, 2+ confluence gates
        // INFO: routine trade
        
        if (ctx.cascadeActive()) return Severity.CRITICAL;
        if (ctx.fundingPercentile30d() > 95 && !signal.direction().equals("LONG")) {
            return Severity.HIGH;  // Shorting into extreme crowd
        }
        return Severity.INFO;
    }
}
```

### 3.2 NotificationFacadeService

**File:** `src/main/java/com/smarttrader/v2/siren/NotificationFacadeService.java`

```java
@Service
@RequiredArgsConstructor
public class NotificationFacadeService {
    
    @EventListener
    public void onOpportunitySiren(OpportunitySirenEvent event) {
        if (event.severity() == Severity.CRITICAL) {
            sendTelegram(event);
            sendEmail(event);
        } else if (event.severity() == Severity.HIGH) {
            sendWebhook(event);
        }
    }
    
    private void sendTelegram(OpportunitySirenEvent event) {
        // POST to Telegram API
    }
    
    private void sendEmail(OpportunitySirenEvent event) {
        // Send email alert
    }
    
    private void sendWebhook(OpportunitySirenEvent event) {
        // POST to configured webhook
    }
}
```

### 3.3 Post-Hoc Opportunity Scoring

**File:** `src/main/java/com/smarttrader/v2/siren/OpportunityScoringJob.java`

Hourly batch job: recompute would-have R for all recent opportunities.

```java
@Component
@RequiredArgsConstructor
public class OpportunityScoringJob {
    
    private final OpportunityRepository opportunityRepository;
    private final ProductService productService;
    
    @Scheduled(fixedRate = 3600000)  // 1 hour
    public void scoreOpportunities() {
        List<Opportunity> unscored = opportunityRepository.findUnscoredOpportunities();
        
        for (Opportunity opp : unscored) {
            List<Candle> candles = productService.getLiveCandles(opp.symbol(), Granularity.ONE_HOUR);
            
            // Compute would-have R at 1h, 4h, 24h
            float r1h = computeWouldHaveR(opp, candles, 1);
            float r4h = computeWouldHaveR(opp, candles, 4);
            float r24h = computeWouldHaveR(opp, candles, 24);
            
            opp.setWouldHaveR1h(r1h);
            opp.setWouldHaveR4h(r4h);
            opp.setWouldHaveR24h(r24h);
            opportunityRepository.save(opp);
        }
    }
    
    private float computeWouldHaveR(Opportunity opp, List<Candle> candles, int hours) {
        // Stub: find entry price, TP, SL in candles, compute R
        return 0.0f;
    }
}
```

### 3.4 OpportunityRepository

**File:** `src/main/java/com/smarttrader/v2/siren/OpportunityRepository.java`

```java
@Repository
public interface OpportunityRepository extends MongoRepository<Opportunity, String> {
    List<Opportunity> findUnscoredOpportunities();
    List<Opportunity> findBySirenCategory(String category);  // for analytics
}
```

### 3.5 Testing Gates

- [ ] OpportunitySiren fires for every strategy signal
- [ ] Severity assignment: CRITICAL for cascades, HIGH for crowd fades
- [ ] Notification delivery: webhook/email/Telegram latency < 1s
- [ ] Post-hoc scoring: would-have R matches manual review on 20 opportunities
- [ ] MongoDB persistence: opportunities stored with TTL 180 days

**Go/No-Go:** Siren end-to-end tested; non-executable opportunities route to notifications.

---

## PHASE 4: VALIDATION PIPELINE (1.5 weeks, PARALLEL with Phase 3 end)

### 4.1 Extend TradeEngine to Support Stages

**File:** `src/main/java/com/smarttrader/v2/engine/TradeEngine.java` (EXISTING, EXTEND)

Add stage checking:
```java
public TradeDecision decide(AnalysisContext ctx, double capital) {
    MarketRegime regime = marketRegimeDetector.detect(ctx);
    
    List<TradingStrategy> strategies = strategySelector.selectStrategies(regime);
    
    for (TradingStrategy strategy : strategies) {
        StrategyStage stage = strategyStateManager.getStage(strategy.getClass().getSimpleName());
        
        // Only execute if stage is not below SHADOW (no orders)
        if (stage == StrategyStage.RESEARCH) {
            continue;  // signal fires (Siren), but no order
        }
        
        SignalResult signal = strategy.evaluate(ctx);
        TradeDecision decision = riskEngine.evaluate(regime, signal, capital);
        
        if (decision.approved()) {
            // Check stage: if MICRO_LIVE or FULL, place order; else Siren only
            if (stage == StrategyStage.SHADOW || stage == StrategyStage.RESEARCH) {
                logShadowSignal(signal);  // no order
            } else {
                return decision;  // order will be placed
            }
        }
    }
    
    return noDecision(regime);
}
```

### 4.2 StrategyStateManager

**File:** `src/main/java/com/smarttrader/v2/strategy/StrategyStateManager.java`

```java
@Service
@RequiredArgsConstructor
public class StrategyStateManager {
    
    private final StrategyStateRepository repository;
    private final TradingEventPublisher eventPublisher;
    
    public StrategyStage getStage(String strategyName, String symbol) {
        StrategyState state = repository.findByStrategyAndSymbol(strategyName, symbol);
        return state != null ? state.stage() : StrategyStage.RESEARCH;
    }
    
    public void promote(String strategyName, String symbol, StrategyStage newStage, String reason) {
        StrategyState state = repository.findByStrategyAndSymbol(strategyName, symbol);
        StrategyStage oldStage = state.stage();
        
        state.setStage(newStage);
        state.setLastPromotedNs(System.nanoTime());
        repository.save(state);
        
        StrategyStageChangedEvent event = new StrategyStageChangedEvent();
        event.strategyName = strategyName;
        event.oldStage = oldStage;
        event.newStage = newStage;
        eventPublisher.publish(event);
    }
    
    public void demote(String strategyName, String symbol, StrategyStage newStage, String reason) {
        // Same as promote, but validates newStage < currentStage
    }
}
```

### 4.3 ValidationPipeline (Backtest Integration)

**File:** `src/main/java/com/smarttrader/v2/validation/ValidationPipelineService.java`

```java
@Service
@RequiredArgsConstructor
public class ValidationPipelineService {
    
    private final BacktestRunner backtestRunner;
    private final StrategyStateManager stateManager;
    private final ShadowModeService shadowMode;
    
    public void validateResearch(String strategyName, String symbol) {
        // Run backtest: ≥ 500 trades, expectancy > 0, param sensitivity, Monte Carlo
        BacktestResult result = backtestRunner.run(strategyName, symbol);
        
        if (result.trades() >= 500
            && result.expectancy() > 0
            && result.paramSensitivity() > 0
            && result.monteCarloRoR() < 0.01) {
            
            stateManager.promote(strategyName, symbol, StrategyStage.SHADOW, "backtest passed");
        }
    }
    
    public void validateShadow(String strategyName, String symbol) {
        // ≥ 4 weeks, ≥ 100 signals, signal distribution ±20% of backtest
        ShadowMetrics metrics = shadowMode.getMetrics(strategyName, symbol);
        
        if (metrics.age() >= Duration.ofDays(28)
            && metrics.signalCount() >= 100
            && metrics.distributionMatch() >= 0.8) {  // ±20%
            
            stateManager.promote(strategyName, symbol, StrategyStage.MICRO_LIVE, "shadow passed");
        }
    }
    
    public void validateMicroLive(String strategyName, String symbol) {
        // ≥ 100 fills, slippage ≤ 1.5× modeled, expectancy > 0
        LiveMetrics metrics = getLiveMetrics(strategyName, symbol);
        
        if (metrics.fills() >= 100
            && metrics.slippage() <= 1.5
            && metrics.expectancy() > 0) {
            
            stateManager.promote(strategyName, symbol, StrategyStage.FULL, "micro-live passed");
        }
    }
}
```

### 4.4 Demotion Rules

Continuous monitoring (runs every candle close):

```java
@Component
@RequiredArgsConstructor
public class StrategyDemotionMonitor {
    
    private final StrategyStateManager stateManager;
    private final TradeRepository tradeRepository;
    
    public void checkDemotionRules(String strategyName, String symbol) {
        List<Trade> recent20 = tradeRepository.findRecent(strategyName, symbol, 20);
        
        double expectancy = computeExpectancy(recent20);
        if (expectancy < 0) {
            stateManager.demote(strategyName, symbol, demoteOneLevel(), "rolling expectancy negative");
        }
        
        double slippage = computeAverageSlippage(recent20);
        if (slippage > 2.0) {
            stateManager.demote(strategyName, symbol, StrategyStage.SHADOW, "slippage > 2×");
        }
        
        double winRate = computeWinRate(recent20);
        double backtestWinRate = getBacktestWinRate(strategyName, symbol);
        if (winRate < (backtestWinRate - 0.15)) {
            stateManager.demote(strategyName, symbol, StrategyStage.SHADOW, "win rate degraded");
        }
    }
}
```

### 4.5 ShadowModeService

**File:** `src/main/java/com/smarttrader/v2/validation/ShadowModeService.java`

Intercepts signals without placing orders:

```java
@Service
@RequiredArgsConstructor
public class ShadowModeService {
    
    private final ShadowSignalRepository repository;
    
    public void logShadowSignal(String strategyName, String symbol, SignalResult signal) {
        ShadowSignal shadow = ShadowSignal.builder()
            .strategyName(strategyName)
            .symbol(symbol)
            .signal(signal)
            .detectedAtNs(System.nanoTime())
            .build();
        
        repository.save(shadow);
    }
    
    public ShadowMetrics getMetrics(String strategyName, String symbol) {
        List<ShadowSignal> signals = repository.findByStrategyAndSymbol(strategyName, symbol, 
            Instant.now().minus(Duration.ofDays(28)));
        
        // Analyze: signal count, distribution vs backtest, etc.
        return computeMetrics(signals);
    }
}
```

### 4.6 Testing Gates

- [ ] Stage promotion logic: RESEARCH → SHADOW → MICRO-LIVE → FULL transitions work
- [ ] Demotion rules: expectancy < 0, slippage, win rate degradation trigger correctly
- [ ] SHADOW signal logging: no orders placed, metrics computed
- [ ] ShadowMetrics: distribution within ±20% of backtest detected
- [ ] Regression: all v2.2 logic unaffected by stage checking

**Go/No-Go:** Validation pipeline end-to-end. Strategies flow through stages correctly.

---

## PHASE 5: LIVE FEEDBACK LOOPS (1 week, PARALLEL with Phase 4 end)

### 5.1 SlippageCalibrator

**File:** `src/main/java/com/smarttrader/v2/feedback/SlippageCalibrator.java`

Runs daily; updates slippage model.

```java
@Component
@RequiredArgsConstructor
public class SlippageCalibrator {
    
    private final TradeRepository tradeRepository;
    
    @Scheduled(cron = "0 0 * * *")  // Daily
    public void calibrateSlippage() {
        for (String symbol : trackedSymbols) {
            List<Trade> last100 = tradeRepository.findRecent(symbol, 100);
            
            double realizedSlippage = computeAverageSlippage(last100);
            double modeledSlippage = getModeledSlippage(symbol);
            
            if (realizedSlippage > modeledSlippage * 2) {
                // Alert: divergence detected
                updateSlippageModel(symbol, realizedSlippage);
                publishConfigChanged("slippage_model", modeledSlippage, realizedSlippage);
            }
        }
    }
    
    private double computeAverageSlippage(List<Trade> trades) {
        // Exponential weight: half-life = 100 fills
        return 0.0;  // stub
    }
}
```

### 5.2 ThresholdDriftEstimator

**File:** `src/main/java/com/smarttrader/v2/feedback/ThresholdDriftEstimator.java`

Runs nightly; detects regime shifts in percentile-based thresholds.

```java
@Component
@RequiredArgsConstructor
public class ThresholdDriftEstimator {
    
    private final CandleRepository candleRepository;
    private final ConfigurationService configService;
    
    @Scheduled(cron = "0 2 * * *")  // 2 AM daily
    public void estimateThresholdDrift() {
        List<Candle> last30d = candleRepository.findLast30Days();
        
        // Recompute ATR percentile distribution
        List<Double> atrValues = extractATRs(last30d);
        int atrPercentile90 = percentile(atrValues, 90);
        
        if (Math.abs(atrPercentile90 - configService.getATRPercentile90()) > 
            configService.getATRPercentile90() * 0.5) {
            // Drift detected; update config
            configService.updateATRPercentile90(atrPercentile90);
            publishConfigChanged("atr_percentile_90", old, atrPercentile90);
        }
    }
}
```

### 5.3 MetaAllocator

**File:** `src/main/java/com/smarttrader/v2/feedback/MetaAllocator.java`

Runs weekly; realloc risk budget per strategy.

```java
@Component
@RequiredArgsConstructor
public class MetaAllocator {
    
    private final StrategyStateRepository stateRepository;
    private final TradeRepository tradeRepository;
    
    @Scheduled(cron = "0 0 * * MON")  // Weekly, Monday
    public void reallocateRiskBudget() {
        List<StrategyState> fullStages = stateRepository.findAllByStage(StrategyStage.FULL);
        
        // Rank by rolling 60-trade expectancy
        List<StrategyRanking> ranked = fullStages.stream()
            .map(s -> new StrategyRanking(s, getRolling60TradeExpectancy(s)))
            .sorted(Comparator.comparingDouble(StrategyRanking::expectancy).reversed())
            .toList();
        
        // Allocate: top 1/3 × 1.2, mid 1/3 × 1.0, bottom 1/3 × 0.6
        for (int i = 0; i < ranked.size(); i++) {
            double multiplier = i < ranked.size() / 3 ? 1.2
                : i < 2 * ranked.size() / 3 ? 1.0
                : 0.6;
            
            updateRiskBudget(ranked.get(i).strategy(), multiplier);
        }
    }
    
    private double getRolling60TradeExpectancy(StrategyState state) {
        List<Trade> last60 = tradeRepository.findRecent(state.strategyName(), 60);
        return computeExpectancy(last60);
    }
}
```

### 5.4 Testing Gates

- [ ] SlippageCalibrator: realized > 2× modeled triggers alert + config update
- [ ] ThresholdDriftEstimator: distribution shift detected ≥ 50% change triggers update
- [ ] MetaAllocator: risk budget reallocation doesn't cause instability
- [ ] ConfigChanged events: persisted with full audit trail, reversible
- [ ] Non-blocking: all Phase 5 jobs run async, don't block live trading

**Go/No-Go:** Feedback loops operational. Slippage and threshold recalibration verified.

---

## PHASE 6: ADVERSARIAL TESTING & DEGRADED MODES (1.5 weeks, PARALLEL with Phase 5)

### 6.1 ChaosInjectorService

**File:** `src/main/java/com/smarttrader/v2/testing/ChaosInjectorService.java`

Injects failures into staging environment:

```java
@Component
public class ChaosInjectorService {
    
    // Scenario 1: WebSocket drop
    public void injectWebSocketDrop(String symbol, long durationMs) {
        // Drop connection for durationMs
        // Assert: no duplicate orders, position reconciled on reconnect
    }
    
    // Scenario 2: Candle gap
    public void injectCandleGap(String symbol, long gapMs) {
        // Skip sending candles for gapMs
        // Assert: CVD marked STALE, strategies skip CVD gates
    }
    
    // Scenario 3: Flash crash replay
    public void injectFlashCrash(String symbol) {
        // Replay 2020-03-12 data
        // Assert: kill-switches fire, cascade holds, no averaging-down
    }
    
    // ... 10+ more scenarios
}
```

### 6.2 BacktestRunner Enhancements

Add degraded-mode labeling to backtest results:

```java
@Component
public class BacktestRunner {
    
    public BacktestResult run(String strategyName, String symbol) {
        return run(strategyName, symbol, Feeds.ALL);
    }
    
    public BacktestResult run(String strategyName, String symbol, Feeds feeds) {
        // Remove feeds per degraded-mode test
        boolean isL2Degraded = !feeds.contains(Feeds.L2);
        boolean isCVDDegraded = !feeds.contains(Feeds.MATCHES);
        
        // Run backtest, mark result
        BacktestResult result = runInternal(strategyName, symbol);
        if (isL2Degraded || isCVDDegraded) {
            result.setDegraded(true);
            result.setLabel("DEGRADED: " + 
                (isL2Degraded ? "L2 " : "") +
                (isCVDDegraded ? "CVD" : ""));
        }
        return result;
    }
}
```

### 6.3 Degraded-Mode Matrix (Integration Test)

```java
@SpringBootTest
public class DegradedModeMatrixTest {
    
    @Test
    public void testSweepReclaimWithoutL2() {
        // Run SweepReclaimStrategy backtest without L2 feed
        BacktestResult degraded = backtestRunner.run("SweepReclaim", "BTC-USD", Feeds.OHLCV_ONLY);
        
        // Degraded result should exist and have lower confidence
        assertThat(degraded.isDegraded()).isTrue();
        assertThat(degraded.label()).contains("L2");
        
        // But strategy should still work (wick/close proxies)
        assertThat(degraded.trades()).isGreaterThan(100);
    }
    
    @Test
    public void testCVDDivergenceWithoutMatches() {
        // CVD divergence gate should skip if matches stream down
        BacktestResult degraded = backtestRunner.run("SFPReversal", "BTC-USD", Feeds.OHLCV_ONLY);
        
        assertThat(degraded.isDegraded()).isTrue();
        // Strategy still fires, but confidence × 0.8
    }
}
```

### 6.4 Testing Gates

- [ ] Chaos suite: all 13 scenarios execute without assertion failure
- [ ] Feed chaos: duplicate/out-of-order/gap injection detected and handled
- [ ] Market chaos: flash crash replay → kill-switches fire, cascade holds
- [ ] Degraded backtest: results labeled correctly, confidence adjusted
- [ ] Regression: v2.2 tests still pass through chaos

**Go/No-Go:** Chaos suite passes 100%. CI gate enforced.

---

## PHASE 7: GO-LIVE & MONITORING (1.5 weeks)

### 7.1 Pre-Go-Live Checklist

- [ ] 3+ strategies (e.g., BreakoutStrategy, SweepReclaimStrategy, RangeHarvesterStrategy) promoted to FULL on BTC-USD, ETH-USD, SOL-USD
- [ ] MetaAllocator has run ≥ 2 weeks (capital allocation stable)
- [ ] SlippageCalibrator has run ≥ 5 days (realized ≤ 1.5× modeled)
- [ ] Siren notifications tested end-to-end (webhook, email, Telegram)
- [ ] Chaos suite: 100% pass in CI
- [ ] Degraded modes tested on staging
- [ ] 2 stakeholders signed off on risk policy
- [ ] Exchange API rate limits understood; retry backoff tested

### 7.2 Canary Phase (Week 1)

1 strategy, 1 symbol (BTC-USD), 0.25% risk per trade (half minimum):

```
- Run 48 hours
- Monitor: fill rate ≥ 95%, slippage ≤ 1.5× modeled, expectancy > 0
- Gate: all metrics green → proceed
```

### 7.3 Gradual Phase (Week 2)

2–3 strategies, 3 symbols, 0.5% risk per trade:

```
- Run 1 week
- Monitor: live win rate ≥ (backtest − 10 points)
- Gate: no adverse surprises → proceed
```

### 7.4 Full Phase (Ongoing)

All 6 strategies, all symbols, full risk per strategy (via MetaAllocator).

### 7.5 Monitoring Dashboard

Real-time + historical:
- Trade latency: signal eval → order submit (p50/p95/p99)
- Fill rates per strategy/symbol
- Realized slippage vs. modeled
- Rolling 20-trade expectancy per strategy
- Feed health (each feed: HEALTHY/STALE/DEGRADED)
- Cascade detection frequency + profitability
- Opportunity Siren post-hoc R distribution

### 7.6 Testing Gates

- [ ] Canary metrics: latency, fill rate, slippage, expectancy all green
- [ ] First 100 live trades reviewed (no anomalies)
- [ ] Post-hoc R on first 50 Siren events: expectancy > 0
- [ ] Feed health: no stale feeds, no orphaned positions

**Go/No-Go:** Canary + gradual phases pass. Operations + risk sign-off.

---

## INCREMENTAL BUILD: LEVERAGED EXISTING CODE

| Component | v2.2 Status | Phase | V2.5 Work |
|---|---|---|---|
| AnalysisContext | ✅ Exists | 0 | ADD: CVD, funding, OI, liquidity, cascade fields |
| MarketRegime enum | ✅ 5 regimes (BREAKOUT, CONTINUATION, PULLBACK, PANIC, DISTRIBUTION) | 0 | ADD: RANGE, CHOP, NEWS_SHOCK, SQUEEZE_LONG, SQUEEZE_SHORT |
| MarketRegimeDetector | ✅ Exists | 2 | EXTEND: add new regime detection logic |
| TradingStrategy interface | ✅ Exists | 2 | REUSE: implement 6 new strategies (SweepReclaim, SFP, RangeHarvester, AntiSMC, ShortSide, CascadeReversal) |
| StrategySelector | ✅ Exists (single strategy) | 2 | EXTEND: return primary + secondary strategy pair per Playbook Matrix |
| TradeEngine | ✅ Exists | 4 | EXTEND: add stage checking (RESEARCH → SHADOW → MICRO-LIVE → FULL) |
| RiskEngine | ✅ Exists | — | REUSE AS-IS (already 1% risk, R:R ≥ 2.0) |
| ProductService | ✅ Fetches candles | 1A | EXTEND: integrate L2 snapshots, matches stream (WebSocket) |
| CoinbaseClient | ✅ OAuth done | 1B | EXTEND: add matches stream, funding/OI endpoints |
| MongoDB | ✅ Configured | 0 | ADD: TTL indexes for liquidity_pools, opportunities, feed_health |
| Caffeine cache | ✅ Configured | 0 | EXTEND: per-subsystem caching (LiquidityMap: 1h, AnalysisContext: 1h) |
| Spring Events | ✅ Implicit (via ApplicationEventPublisher) | 0 | FORMALIZE: TradingEvent base class, event versioning |
| Test infrastructure | ✅ JUnit5, Mockito, MockWebServer | 5-6 | EXTEND: backtest runner, shadow mode service, chaos injection |

---

## TIMELINE SUMMARY

| Phase | Duration | FTE | Dependencies | Go/No-Go Criteria |
|---|---|---|---|---|
| 0 | 1 week | 1 | None | All v2.2 tests pass; new fields backward-compatible |
| 1A | 2 weeks | 2 | Phase 0 | Liquidity map accuracy ≥ 95%; sweep detection ≥ 90% recall |
| 1B | 1.5 weeks | 1.5 | Phase 0, 1A.2 | All crowd feeds reach AnalysisContext with ≤ 10ms latency |
| 2 | 2 weeks | 2 | Phase 1B | 6 strategies backtest expectancy > 0; playbook matrix routes correctly |
| 3 | 1.5 weeks | 1 | Phase 2 | Siren end-to-end; non-executable opportunities route to notifications |
| 4 | 1.5 weeks | 1.5 | Phase 3 | Validation pipeline: stages flow correctly; demotion rules work |
| 5 | 1 week | 1 | Phase 4 | Feedback loops async; no trading latency impact |
| 6 | 1.5 weeks | 2 | Phase 5 | Chaos suite 100% pass; degraded modes verified |
| 7 | 1.5 weeks | 2 | Phase 6 | Canary + gradual phases pass; first 100 trades clean |
| **TOTAL** | **~13 weeks** | **avg 1.5 FTE** | — | — |

**Critical path (no parallelization):** Phase 0 → 1A → 1B → 2 → 4 → 7 = ~10 weeks

**With parallelization:** 1B ∥ 1A.2, 2 ∥ 1B, 3 ∥ 2, 4 ∥ 3, 5 ∥ 4, 6 ∥ 5 = **~7–8 weeks with 2 FTE**

---

## NO REWORK: PRESERVED v2.2 ARCHITECTURE

- ✅ BreakoutStrategy, ContinuationStrategy, PullbackStrategy untouched (tests still pass)
- ✅ TradeEngine core logic untouched (stage checking added as orthogonal layer)
- ✅ RiskEngine untouched (R:R filtering, position sizing reused)
- ✅ CoinbaseClient interface unchanged (only implementation extended)
- ✅ MongoDB + Caffeine + Spring Boot baseline stable

**All new code is additive.** If Phase 7 fails, rollback is trivial (disable new strategies via config).

---

## QUICK START: PHASE 0 TODAY

1. Edit `AnalysisContext.java`: add CVD, funding, OI, liquidityMap fields
2. Edit `MarketRegime.java`: add RANGE, CHOP, NEWS_SHOCK, SQUEEZE_LONG, SQUEEZE_SHORT
3. Create `src/main/java/com/smarttrader/v2/event/TradingEvent.java` (base class)
4. Create `src/main/java/com/smarttrader/v2/model/LiquidityPool.java`
5. Run all v2.2 tests: verify no regressions
6. Go/No-Go: proceed to Phase 1A when ready

**Estimated effort:** 2–3 hours.
