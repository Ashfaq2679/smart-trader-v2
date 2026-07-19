# Smart Trader V2

Institutional-style algorithmic crypto trading decision engine built on Java 17 / Spring
Boot 3 / MongoDB, layered on the Coinbase Advanced Trade REST API. Price action, market
structure, liquidity, and crowd-positioning drive decisions — indicators confirm price,
they never replace it.

## Data Flow Diagram

```mermaid
flowchart TB
    Coinbase[("Coinbase Advanced Trade API")]
    Scheduler["TradingScheduler\n(per-granularity @Scheduled poll:\n1m/5m/15m/30m/1h/4h, configurable)"]

    subgraph Ingestion["Market Data Ingestion"]
        ProductService["ProductService"]
        LiquidityMapper["LiquidityMapperService"]
        SweepDetector["SweepDetectorService"]
        CVD["CVDCalculatorService"]
        Funding["FundingMonitorService"]
        OI["OIMonitorService"]
        Absorption["AbsorptionDetectorService"]
    end

    ContextBuilder["AnalysisContextBuilder\n(builds AnalysisContext snapshot)"]

    subgraph Decision["Decision Pipeline"]
        RegimeDetector["MarketRegimeDetector"]
        StrategySelector["StrategySelector"]
        Strategies["TradingStrategy impls\n(Pullback, Breakout, Continuation,\nSweepReclaim, SFPReversal, RangeHarvester,\nAntiSMC, ShortSide, CascadeReversal)"]
        RiskEngine["RiskEngine"]
        TradeEngine["TradeEngine"]
    end

    subgraph Siren["Opportunity Siren"]
        SirenService["OpportunitySirenService"]
        NotifyFacade["NotificationFacadeService"]
        ScoringJob["OpportunityScoringJob"]
    end

    subgraph Validation["Validation Pipeline"]
        StateManager["StrategyStateManager"]
        ShadowMode["ShadowModeService"]
        ValidationPipeline["ValidationPipelineService"]
        DemotionMonitor["StrategyDemotionMonitor"]
        BacktestRunner["BacktestRunner\n(stubbed - no data yet)"]
    end

    subgraph Feedback["Live Feedback Loops"]
        SlippageCal["SlippageCalibrator"]
        ThresholdDrift["ThresholdDriftEstimator"]
        MetaAllocator["MetaAllocator"]
    end

    subgraph Execution["Execution Layer"]
        OrderService["OrderService\n(dry-run by default;\nlive-enabled flips it on)"]
        PositionService["PositionService"]
        CoinbaseOrders[("Coinbase Advanced Trade SDK\n(ES256 JWT, CDP API key)")]
    end

    Mongo[("MongoDB\nliquidity_pools, opportunities,\nstrategy_states, shadow_signals,\ntrade_outcomes, config_change_records,\norders, positions")]
    EventBus{{"TradingEventPublisher\n(Spring ApplicationEventPublisher)"}}

    Scheduler -->|poll per configured interval| ContextBuilder
    Scheduler -->|decide| TradeEngine
    Scheduler -->|execute approved decision| OrderService
    OrderService -->|dry-run: log + persist only| Mongo
    OrderService -->|live-enabled=true: place MARKET order| CoinbaseOrders
    OrderService --> EventBus
    OrderService -->|order placed| PositionService
    PositionService --> Mongo
    PositionService --> EventBus
    EventBus -.->|ExecutionDegradedEvent: BOLD alert| NotifyFacade
    Coinbase -->|REST: candles| ProductService
    ProductService --> ContextBuilder
    ProductService --> LiquidityMapper
    LiquidityMapper --> SweepDetector
    LiquidityMapper --> Mongo
    SweepDetector --> EventBus
    ProductService --> CVD
    ProductService --> Funding
    ProductService --> OI
    ProductService --> Absorption

    CVD --> ContextBuilder
    Funding --> ContextBuilder
    OI --> ContextBuilder
    Absorption --> ContextBuilder
    LiquidityMapper --> ContextBuilder

    ContextBuilder -->|AnalysisContext| RegimeDetector
    RegimeDetector -->|MarketRegime| StrategySelector
    StrategySelector --> Strategies
    Strategies -->|SignalResult| RiskEngine
    RiskEngine -->|TradeDecision| TradeEngine

    Strategies -.->|non-NONE signal| SirenService
    SirenService --> Mongo
    SirenService --> EventBus
    EventBus --> NotifyFacade
    ScoringJob --> Mongo
    ScoringJob --> ProductService

    StrategySelector -.->|playbook selection\n(Phase 4, not yet wired)| StateManager
    StateManager --> Mongo
    StateManager --> EventBus
    ShadowMode --> Mongo
    ValidationPipeline --> BacktestRunner
    ValidationPipeline --> ShadowMode
    ValidationPipeline --> StateManager
    DemotionMonitor --> Mongo
    DemotionMonitor --> StateManager

    SlippageCal --> Mongo
    SlippageCal --> EventBus
    ThresholdDrift --> ProductService
    ThresholdDrift --> EventBus
    MetaAllocator --> Mongo
    MetaAllocator --> StateManager

    classDef external fill:#e8e8e8,stroke:#888,color:#111;
    classDef store fill:#dbe9ff,stroke:#4a7cc7,color:#111;
    classDef bus fill:#ffe9c2,stroke:#c78a2b,color:#111;
    classDef scheduler fill:#e0f5e0,stroke:#4a9c4a,color:#111;
    classDef execution fill:#ffd9d9,stroke:#c74a4a,color:#111;
    class Coinbase external;
    class CoinbaseOrders external;
    class Mongo store;
    class EventBus bus;
    class Scheduler scheduler;
    class OrderService execution;
    class PositionService execution;
```

### Reading the diagram

0. **TradingScheduler** — the entry point that actually drives the pipeline on a timer.
   Each of 1m/5m/15m/30m/1h/4h has its own independent enable flag and interval
   (`smart-trader.scheduler.granularities.*` in `application.yml`), plus a single global
   `smart-trader.scheduler.enabled` kill switch — both default to disabled, so nothing
   polls Coinbase until explicitly configured. For every tracked symbol
   (`smart-trader.v2_5.tracked-symbols`) it calls `AnalysisContextBuilder.build()` →
   `TradeEngine.decide()` → `OrderService.execute()` on any approved decision.
1. **Market Data Ingestion** — `ProductService` is the single REST gateway to Coinbase
   (candles only; no live WebSocket feed exists in this codebase). Liquidity mapping,
   sweep detection, CVD, funding, open interest, and absorption all derive from that
   candle data and feed into a single `AnalysisContext` snapshot per evaluation.
2. **Decision Pipeline** — `TradeEngine` orchestrates regime detection → strategy
   selection → signal evaluation → risk filtering → a `TradeDecision`, per
   `V2_TECH_SPEC.md` section 5. This is the only path that can approve a trade.
2.5. **Execution Layer** — `OrderService` turns an approved `TradeDecision` into a real
   MARKET order via the official Coinbase Advanced Trade SDK (CDP API key, ES256 JWT
   auth — no hand-rolled signing). Defaults to **dry-run** (`smart-trader.execution.
   live-enabled=false`): every approved decision is logged/persisted, nothing reaches
   Coinbase. If live mode is on and a real order still can't be placed for any reason
   (missing credentials, a Coinbase rejection, an exception), that's the system moving
   away from its stated target, so it fires `ExecutionDegradedEvent` —
   `NotificationFacadeService` renders this as a **BOLD banner** in the logs, never a
   quiet line. `PositionService` opens a `Position` from every real (non-dry-run) filled
   order; it does not yet watch live price against a position's stored stop/target to
   close it automatically — that needs a monitoring loop this codebase doesn't have yet.
3. **Opportunity Siren** — every strategy signal with a real direction (LONG/SHORT),
   executable or not, is persisted and published so nothing "whispers" past unexecutable
   setups (e.g. a short on a long-only venue).
4. **Validation Pipeline** — gates a strategy's promotion from RESEARCH → SHADOW →
   MICRO_LIVE → FULL. `BacktestRunner` is currently a documented fail-safe stub (no
   candle-replay backtest engine exists yet), so RESEARCH-stage promotion is
   intentionally never automatic on faked data.
5. **Live Feedback Loops** — scheduled jobs that recalibrate slippage/threshold models
   and reallocate risk budget across FULL-stage strategies from real trade-outcome data.
6. Dotted arrows mark integrations that exist as standalone, tested components but are
   **not yet wired into `TradeEngine`** (its constructor and behavior are pinned by
   existing tests) — a deliberate, documented deferral rather than an oversight.

## Module Layout

| Package | Responsibility |
|---|---|
| `client` | Coinbase Advanced Trade REST client |
| `scheduler` | `TradingScheduler` — configurable-interval candle polling per granularity |
| `service` | Read-through market data access (`ProductService`) |
| `liquidity` | Liquidity pool mapping and sweep detection |
| `positioning` | CVD, funding, open interest, absorption |
| `context` | Builds the immutable `AnalysisContext` snapshot |
| `regime` | Market regime classification |
| `strategy` | `TradingStrategy` implementations + selection |
| `risk` | R:R filtering and position sizing |
| `engine` | `TradeEngine` orchestration |
| `execution` | `OrderService` (dry-run/live market orders via the Coinbase SDK), `PositionService` |
| `siren` | Opportunity Siren, notifications, post-hoc scoring, BOLD execution alerts |
| `validation` | Strategy stage lifecycle, shadow mode, demotion |
| `feedback` | Live slippage/threshold recalibration, risk allocation |
| `event` | Shared domain event base + publisher |
| `model` | Domain records/documents |
| `constants` | Per-subsystem tunable constants (all "validated: false" pending sensitivity analysis) |

## Going live

Everything defaults to off. To actually poll and trade:

```yaml
smart-trader:
  v2_5:
    tracked-symbols: [BTC-USD, ETH-USD]
  scheduler:
    enabled: true
    granularities:
      fifteen-minute: { enabled: true }
  execution:
    live-enabled: true   # false = dry-run (logged/persisted, nothing sent to Coinbase)

coinbase:
  api:
    key-name: ${COINBASE_API_KEY_NAME}       # CDP Cloud API key
    private-key: ${COINBASE_API_PRIVATE_KEY} # PEM-encoded EC private key, never hardcoded
    portfolio-id: ${COINBASE_PORTFOLIO_ID}
```

If `execution.live-enabled=true` but `key-name`/`private-key` aren't configured, or a
live order fails, `OrderService` doesn't fail silently — it publishes
`ExecutionDegradedEvent`, which `NotificationFacadeService` logs as a BOLD banner.

## Architecture principles

See [CLAUDE.md](CLAUDE.md) for the full set of engineering principles this codebase
follows (price as source of truth, strategies never call repositories directly,
constructor injection only, etc.).
