# V2_TECH_SPEC.md (v2.5.1 — Whale-Side / All-Weather Edition, Confidence-Hardened)

Supersedes v2.2. Everything in v2.2 remains in force unless explicitly overridden here.
v2.5.1 adds §§11–14: data-dependency guarantees, a validation/promotion pipeline, live feedback loops, and adversarial testing — the difference between rules that *read* right and rules that are *proven* right.

**Design premise of v2.5:** v2.2 detects volatility. v2.5 understands *who causes it and why*. Large players (whales) manufacture moves to harvest retail liquidity: they sweep stops below support before rallying, spring fake breakouts to trap breakout-chasers, and engineer panic to buy the flush. Smart Money Concepts (SMC) traders sit one level above retail playing exactly these patterns — and their playbook is now public and crowded, which makes *them* predictable too. v2.5 positions the system on the whale side of the order book: trade **into** liquidity events, not **behind** them, and extract profit from every regime — up, down, and sideways. There is no "bad market", only an unread one.

---

# PART A — Adversarial Review: How a Whale Would Bankrupt the v2.2 Bot

I ran v2.2 through the eyes of the counterparty. Findings:

| # | v2.2 weakness | How the whale exploits it | v2.5 counter |
|---|---|---|---|
| 1 | Breakout entry = stop-market above the trigger-bar high | Textbook trap. I push price 0.3 × Average True Range (ATR) above resistance into the stop cluster, fill your buy as my exit, dump. Your "confirmation" IS my exit liquidity | §5.1 Acceptance rule + §5.2 Sweep-and-Reclaim entry (enter where the trap springs, not where it's set) |
| 2 | Support/resistance treated as barriers | To a whale, obvious levels are **targets**: that's where the resting stops (fuel) are. v2.2 has no map of where liquidity pools sit | §3 Liquidity Map — Equal Highs (EQH), Equal Lows (EQL), session extremes, round numbers, liquidation clusters |
| 3 | RANGE regime only has a timid Range-Fade; CHOP = NO_TRADE | Sideways is a whale accumulation/distribution machine: buy the dip at the swept low, sell the top at the swept high, twice a day, every day. v2.2 leaves the most repeatable P&L on the table | §5.5 Range Harvester — bidirectional, sweep-triggered, both edges |
| 4 | Falling markets: shorts specified but muted; if venue can't short, system stays silent | Down moves in crypto are 1.5–2× faster than up moves (long liquidation cascades). Silence during the highest-velocity opportunity of the week is inexcusable | §6 Short-Side Engine + §7 Opportunity Siren — system must call out LOUDLY even when it cannot execute |
| 5 | No crowd-positioning data (funding, Open Interest (OI), long/short ratio) | I read funding to know when retail is max-long, then I flush them. v2.2 can't see the crowd, so it usually IS the crowd | §4 Crowd Positioning inputs; fade-the-crowd triggers |
| 6 | No order-flow confirmation | Volume spike says "activity"; it doesn't say who won. Absorption (heavy selling into a non-falling bid) is invisible to v2.2 | §4 Cumulative Volume Delta (CVD) + absorption/divergence rules |
| 7 | No concept of engineered wicks / Swing Failure | The single most reliable whale footprint — sweep of a prior extreme that closes back inside — is not even detectable in the v2.2 context | §5.3 Swing Failure Pattern (SFP) Reversal strategy |
| 8 | SMC traders themselves are not modeled | Order Blocks (OB) and Fair Value Gaps (FVG) are marked identically by a million SMC retail traders. Their entries and stops are as mappable as classic retail's | §5.6 Anti-SMC module: fade failed Order Blocks, front-run FVG fills |
| 9 | News shock = blanket 3-bar NO_TRADE | The first orderly pullback after a liquidation cascade is one of the best long entries that exists. Blanket avoidance donates it to whales | §6.3 Cascade-Reversal playbook (with strict guards) |
| 10 | Every regime maps to at most one idea | A professional desk has a playbook page for *every* condition. Blank pages = idle capital | §2 Playbook Matrix — a defined action for all 8 regimes |

Net verdict: v2.2 is a disciplined retail bot. Disciplined retail is still the fuel. v2.5 flips the seat.

---

# PART B — Specification v2.5

## Glossary (Full Forms — use both on first reference in code docs and logs)

| Abbreviation | Full form |
|---|---|
| ATR | Average True Range |
| EMA | Exponential Moving Average |
| ADX | Average Directional Index |
| BB | Bollinger Bands |
| RSI | Relative Strength Index |
| VWAP | Volume-Weighted Average Price |
| OI | Open Interest |
| CVD | Cumulative Volume Delta |
| SMC | Smart Money Concepts |
| SFP | Swing Failure Pattern |
| OB | Order Block |
| FVG | Fair Value Gap |
| EQH / EQL | Equal Highs / Equal Lows |
| HTF / LTF | Higher / Lower Timeframe |
| R:R | Risk-to-Reward ratio |
| SL / TP | Stop Loss / Take Profit |
| L2 | Level-2 order book (depth) |
| ETF / STF / BTF | Execution / Signal / Bias Timeframe (§1 of v2.2) |

---

## 1. Timeframes, Data, Context

Unchanged from v2.2 §1, plus new AnalysisContext fields:

- `cvd_1m`, `cvdSlope_5m` — Cumulative Volume Delta (CVD): running Σ(buy-taker volume − sell-taker volume) from the matches stream; slope = linear regression over 20 bars
- `fundingRateBps`, `fundingPercentile30d` — perpetual funding (from Coinbase International / any perp feed; read-only market intelligence even though execution is spot)
- `oiChange1h`, `oiChange24h` — Open Interest (OI) percent change
- `longShortRatio` — where available; else derived proxy = fundingPercentile
- `liquidityMap` — object, §3
- `vwapSession` — session-anchored Volume-Weighted Average Price (VWAP) ± 1σ, 2σ bands
- `cascadeActive` — boolean, §6.3

## 2. Playbook Matrix (NEW — the core of v2.5)

Every regime has a defined offensive action. NO_TRADE survives only for genuinely toxic tape.

| Regime (v2.2 §3, extended) | Primary play | Secondary play |
|---|---|---|
| TREND_UP | Pullback longs at EMA21/VWAP | Continuation longs |
| TREND_DOWN | **Pullback SHORTS at EMA21/VWAP** (or Siren §7 if venue long-only) | Rally-fade at swept EQH |
| BREAKOUT_UP / _DOWN | Sweep-and-Reclaim entry §5.2; Acceptance entry §5.1 | Compression-Break (v2.2 §4.2) |
| RANGE | **Range Harvester §5.5 — buy swept dip, sell swept top, both directions** | SFP Reversal §5.3 at edges |
| CHOP (toxic only: spreadBps > 8 OR band < 1.5 × ATR) | Stand aside | Accumulate liquidity map data |
| NEWS_SHOCK | 3-bar observation (unchanged) | Then Cascade-Reversal §6.3 if criteria met |
| SQUEEZE_LONG (new: fundingPercentile > 90 AND oiChange24h > +15%) | Prepare shorts / defensive flatten of longs; Siren | SFP short at EQH sweep |
| SQUEEZE_SHORT (new: fundingPercentile < 10 AND oiChange24h > +15%) | Prepare longs into flush | Cascade-Reversal long |

`OpportunityDetected` event fires whenever any playbook cell activates — including cells the venue cannot execute (§7).

## 3. Liquidity Map (NEW)

Maintained per symbol, rolling 5 days, updated each closed Signal Timeframe (STF) bar. Levels are **magnets and fuel**, not walls:

- **Swing extremes:** fractal highs/lows (2-bar left/right) on 15m and 1h
- **Equal Highs (EQH) / Equal Lows (EQL):** ≥ 2 extremes within 0.15 × Average True Range (ATR) of each other → marked as a liquidity pool (density = count × recency weight)
- **Session extremes:** prior day high/low, Asia/EU/US session high/low, prior week high/low
- **Round numbers:** e.g., multiples of $1,000 (BTC), $100 (ETH), configurable per symbol
- **Stop-cluster estimate:** pool density score 0–100 = normalized (touches × volume at level × age decay λ=0.8/day)

Rules of engagement:
1. **Never place the system's own Stop Loss (SL) inside a pool** with density > 60. Offset beyond it by 0.35 × ATR — our stop must survive the sweep that triggers everyone else's.
2. A signal whose Take Profit (TP) path crosses a dense pool gets TP1 set 0.1 × ATR **in front of** the pool (exit into the magnet's pull, before the reversal).
3. Sweep of a pool (trade beyond it followed by close back inside within 2 bars) emits `LiquiditySweepDetected { level, side, density, reclaimed }` — the primary trigger for §5.2, §5.3, §5.5.

## 4. Crowd Positioning & Order Flow (NEW)

The whale's real edge is knowing where the crowd is. Deterministic reads:

- **Crowded-long tape:** `fundingPercentile30d > 90` — treat all long breakout signals as suspect (confidence × 0.5); short-side signals get confidence × 1.25
- **Crowded-short tape:** `fundingPercentile30d < 10` — mirror of the above
- **CVD confirmation:** breakout long valid only if `cvd_1m` made a new 20-bar high with price (participation). Price high without a CVD high = **CVD divergence** → downgrade to trap-watch, arm §5.3 short
- **Absorption:** ≥ 2.5 × relative volume sell-taker flow while price declines < 0.25 × ATR → `AbsorptionDetected(BID)` → long bias for §5.3 / §5.5 at that level (someone big is buying the panic). Mirror for ask-side absorption.
- **Open Interest (OI) logic:** price up + OI up = real trend (new money); price up + OI down = short-covering rally, do not chase, fade at next pool. Same table inverted for down moves. Encode as `oiConfirms` boolean per direction.

## 5. Strategy Set (revised & extended — all bidirectional)

Inherits v2.2 gates (§4 items 1–6). New/changed:

### 5.1 Breakout — Acceptance entry (replaces naive stop-market)
- Require **acceptance**: 2 consecutive Execution Timeframe (ETF) closes beyond the level, OR 1 close beyond with `cvd` new high AND `oiConfirms`
- Entry: limit at retest of the broken level (± 0.15 × ATR), valid 3 bars; SL beyond the reclaim invalidation point, cap 1.2 × ATR
- If price runs without retest → let it go. Chasing is the retail tell.

### 5.2 Sweep-and-Reclaim (NEW — trade the trap, not the bait)
- Trigger: `LiquiditySweepDetected` on a pool with density ≥ 50, reclaim close back inside within 2 bars, `relVolume ≥ 1.5` on the reclaim bar
- Direction: **against** the sweep (sweep below EQL → long; sweep above EQH → short)
- Entry: market on reclaim close; SL 0.35 × ATR beyond the sweep extreme (never inside the pool); TP1 at the opposite side of the local range (minimum effective R:R 1.5); runner per v2.2 exit ladder
- This is the highest-conviction setup in the book: the whale just showed their hand and paid for our entry.

### 5.3 Swing Failure Pattern (SFP) Reversal (NEW)
- Definition: bar takes out a prior 15m/1h swing extreme intrabar by ≥ 0.1 × ATR but **closes back beyond it**, wick ratio ≥ 0.5
- Confluence required (≥ 1): CVD divergence at the extreme, AbsorptionDetected, fundingPercentile extreme (> 90 for shorts / < 10 for longs)
- Entry: close of SFP bar; SL beyond the wick + 0.1 × ATR; TP1 = 1.2R; time stop 8 ETF bars

### 5.4 Pullback / Continuation / Compression-Break
- Unchanged from v2.2 §§4.2–4.4, with one upgrade: pullback zone extended to include session VWAP and 1σ band; confluence of EMA21 + VWAP raises confidence × 1.2.

### 5.5 Range Harvester (REPLACES Range-Fade — the sideways cash machine)
Sideways markets are not dead markets; they are the whale's accumulation lane and should be ours.
- Preconditions: RANGE regime, band width ≥ 2 × ATR, ≥ 2 prior touches per edge
- **Buy the dip:** trigger = sweep OR touch of range low ± 0.3 × ATR with rejection (wick ≥ 0.5) or AbsorptionDetected(BID). Entry limit at edge; SL 0.7 × ATR below sweep extreme; TP1 mid-range (close 60%), TP2 opposite edge − 0.1 × ATR
- **Sell the top:** exact mirror at range high (short if venue allows; else Siren §7 + flatten any longs from the same range)
- Max 2 round-trips per side per session; disable after a confirmed acceptance beyond either edge (§5.1 fires instead — range is dead, long live the trend)
- Never harvest while `cascadeActive` or Volatility State = EXPANSION.

### 5.6 Anti-SMC Module (NEW — beating the players who beat retail)
Smart Money Concepts (SMC) retail marks the same Order Blocks (OB) and Fair Value Gaps (FVG) from the same YouTube rules. Their crowding is measurable and fadeable:
- Track OBs: last opposite-color 15m candle before an impulsive move (≥ 2 × ATR); track FVGs: 3-bar gaps ≥ 0.5 × ATR
- **Front-run the fill:** entries resting at an OB/FVG get placed 0.1 × ATR *before* the zone's near edge (SMC limit orders are AT the edge; we are first in queue)
- **Fade the failure:** price trades fully through an OB (close beyond far edge) → the trapped SMC cohort's stops are behind it → treat the far edge as a fresh liquidity pool for §5.2
- FVG mid-fill + SFP confluence = grade-A reversal entry
- All Anti-SMC entries obey the standard gate stack; this module supplies *levels*, not permission.

## 6. Short-Side Engine (NEW — falling markets are the fastest markets)

### 6.1 Venue capability
- Config: `venue.canShort` (spot Coinbase Advanced Trade = false; margin/perp venue = true)
- `canShort = true` → all short strategies execute natively, same risk stack, sizing × 0.8 (down-tape slippage is worse)
- `canShort = false` → shorts become **Siren-grade alerts (§7) + defensive automation**: cancel all resting long entries, tighten trails on open longs to 0.5 × ATR, optionally rotate to cash per `defensivePolicy` config. The system NEVER stays silent in a falling market.

### 6.2 Down-regime detection (loud, early)
- TREND_DOWN + `oiConfirms(down)` + CVD 20-bar low → `OpportunityDetected(SHORT, severity=HIGH)`
- 1h close below prior week low → severity=CRITICAL (macro breakdown)

### 6.3 Cascade-Reversal playbook (buying the flush / riding the waterfall)
- `cascadeActive = true` when: 1m bar range > 3 × ATR(14)_1m AND `oiChange1h < −5%` (forced liquidations, not new positioning) AND `relVolume ≥ 4`
- During cascade: no entries (unchanged)
- **Ride:** if `canShort` and cascade begins from SQUEEZE_LONG regime → short the first 0.3 × ATR pullback, SL above pullback high, exit 50% on each new leg low, full exit on first `AbsorptionDetected(BID)`
- **Reversal:** after cascade, long permitted only when ALL hold: `oiChange1h` stabilizes (|Δ15m| < 1%), AbsorptionDetected(BID), SFP of the flush low, funding reset below 50th percentile. Size × 0.5. This is the "orderly pullback after the panic" — the trade whales built the panic for.

## 7. Opportunity Siren (NEW — "call it out loudly")

The system must never see an opportunity and whisper.

- Event: `OpportunityDetected { symbol, playbook, direction, severity ∈ {INFO, HIGH, CRITICAL}, executable: bool, reason, contextSnapshot }`
- **Non-executable opportunities (e.g., shorts on a long-only venue) still fire at full severity** — routed to notification channels (webhook/Telegram/e-mail per config) with the exact thesis and levels, so the human can act on another venue
- CRITICAL severities also emit `DefensiveActionTaken` events documenting what the bot did to protect open inventory
- Every Siren is persisted to `opportunity_log` (TTL 180 days) and scored post-hoc: would-have R multiple computed at +1h/+4h/+24h. **Missed-opportunity analytics is a first-class report** — the cheapest edge is the one you already saw and ignored.

## 8. Risk Stack (deltas to v2.2 §6)

- Sweep-and-Reclaim (§5.2) and SFP (§5.3): minimum effective Risk-to-Reward (R:R) 1.5 to TP1
- Range Harvester: min 1.2 to TP1 (mid-range), but win-rate floor enforced — auto-disable per symbol if 20-trade rolling win rate < 55%
- Crowd-positioning multiplier: any strategy fighting a > 90th-percentile funding crowd gets size × 1.1 (the crowd is the fuel); any strategy joining it gets size × 0.6
- Kill-switch hierarchy of v2.2 §6.3 unchanged and applies to short side identically
- Cascade trades: risk 0.25% (half of base) regardless of confidence.

## 9. Execution, Data Integrity, Backtest, Events (deltas)

- Matches-stream ingestion mandatory (CVD is built from it) — v2.2 §7 WebSocket policy already requires it; now load-bearing
- New events: `LiquiditySweepDetected`, `AbsorptionDetected`, `OpportunityDetected`, `DefensiveActionTaken`, `CascadeStateChanged` — all idempotent, timestamped, correlated, schema-versioned
- Backtest must replay matches (tick) data for CVD/absorption strategies; where only candles exist, §5.2/5.3 run in degraded mode (wick-and-close proxies) and results must be labeled DEGRADED in reports
- Liquidity Map, funding, and OI snapshots are persisted inputs — replay identity (v2.2 Final Insight) extends to them
- Walk-forward validation per strategy per regime cell of the Playbook Matrix (§2): a strategy is only enabled live in cells where its out-of-sample expectancy > 0.

## 11. Data Dependency & Degraded-Mode Matrix (NEW)

Every strategy declares its feed dependencies; missing feeds degrade capability deterministically — never silently.

| Feed | Source | If unavailable |
|---|---|---|
| Candles + trades (matches) | Coinbase Advanced Trade WebSocket | System halt — hard dependency |
| Level-2 (L2) order book | Coinbase Advanced Trade WebSocket | Disable `bookImbalance` gate + absorption detection; §5.2/5.3 run on wick/close proxies, size × 0.75, results tagged DEGRADED |
| Cumulative Volume Delta (CVD) | Built from matches stream | If matches gap > 60s: CVD marked STALE, all CVD gates skipped-and-logged, confidence × 0.8 |
| Funding / Open Interest (OI) | External (Coinbase International, Binance, Bybit — read-only, config-selected) | SQUEEZE regimes disabled; crowd multipliers = 1.0; Siren notes "positioning data unavailable" |
| Long/short ratio | External, optional | Fall back to funding percentile proxy (already specified §1) |
| Macro event calendar | Config-loaded file/API | `eventBlackout` never true → compensate: NEWS_SHOCK size × 0.5 globally |

Rules:
- Startup emits `DataCapabilityReport` event listing every feed, its status, and which strategies are consequently OFF/DEGRADED. This report is re-emitted on any feed state change.
- A strategy may never consume a STALE input silently; every skipped gate is recorded in the DecisionSnapshot.
- External derivative feeds (funding/OI) are market intelligence only — never execution triggers on their own, and their outage must not stop spot trading.

## 12. Validation & Promotion Pipeline (NEW — no rule trades real size until it earns it)

Every strategy × Playbook-Matrix cell moves through four stages. Promotion and demotion are automatic and event-logged (`StrategyStageChanged`).

| Stage | Capital | Promotion criteria (all required) |
|---|---|---|
| 1. RESEARCH (backtest) | none | ≥ 500 trades out-of-sample; expectancy > 0 after costs; parameter sensitivity ±20% does not flip expectancy sign; Monte Carlo (10,000 resamples of trade sequence): 95th-percentile max drawdown < 2 × backtest max drawdown, risk-of-ruin (< 50% equity) < 1% |
| 2. SHADOW (live signals, no orders) | none | ≥ 4 weeks AND ≥ 100 signals; live signal distribution within 20% of backtest (trade frequency, avg R, win rate) — detects implementation shift and feed-quality gaps |
| 3. MICRO-LIVE | risk 0.1% per trade | ≥ 100 fills; realized slippage ≤ 1.5 × modeled; live expectancy > 0; no kill-switch triggers attributable to the strategy |
| 4. FULL | risk per §8 / v2.2 §6.2 | Continuous monitoring (below) |

Demotion (automatic, any stage):
- 20-trade rolling expectancy < 0 → drop one stage
- Realized slippage > 2 × modeled for 20 trades → back to SHADOW
- Live win rate < backtest win rate − 15 points (same regime mix) → back to SHADOW
- Any data feed the strategy depends on degrades → strategy caps at MICRO-LIVE while degraded

Hand-set thresholds in §§2–8 are **initial priors, not truths**: each named threshold carries a config flag `validated: false` until Stage-1 sensitivity analysis has covered it; unvalidated thresholds force the owning strategy to cap at SHADOW.

## 13. Live Feedback Loops (NEW — the spec recalibrates itself)

- **Slippage model calibration:** compare realized vs modeled slippage per symbol per order type, exponentially weighted (half-life 100 fills); recalibrated value feeds §6.1 of v2.2 automatically; divergence > 2× alerts
- **Threshold drift re-estimation:** percentile-based thresholds (ATR percentile, Bollinger-width percentile, funding percentile, relVolume) recompute their reference distributions on a rolling 30-day window nightly — a threshold tuned to 2025 volatility must not still be running in 2027's regime
- **Strategy meta-allocator:** weekly, each FULL-stage strategy's risk budget is scaled by rolling 60-trade expectancy rank: top third ×1.2, middle ×1.0, bottom third ×0.6 (floor 0.25%, cap 1.5× base). Empirically underperforming edges shrink without human intervention
- **Missed-opportunity loop closure:** §7's would-have R scoring feeds a monthly report; any Siren category with realized would-have expectancy > 1.5R over ≥ 30 events is flagged as a candidate for venue expansion (e.g., enabling a derivatives venue for shorts)
- All recalibrations are `ConfigChanged` events — auditable, replayable, reversible.

## 14. Failure & Adversarial Testing (NEW — assume the market and the infrastructure are both hostile)

Mandatory chaos suite, run in CI against the replay engine and quarterly against staging:

- **Feed chaos:** WebSocket drop mid-position; 30s candle gap; duplicate + out-of-order trades; L2 snapshot desync — assert: no duplicate orders, positions reconciled, entries blocked until integrity restored (v2.2 §8)
- **Market chaos:** replay of historical flash crashes (e.g., cascade days) through the full stack — assert: kill switches fire in order, cascade guards hold, no averaging-down path exists
- **Bad-tick injection:** single prints ± 20% off-market — assert: rejected by price-gap validation, no stop triggered off a bad print (stops require 2 ticks or 1s persistence beyond level, config)
- **Exchange behavior:** order rejects, partial-fill-then-cancel, 429 rate-limit storms, halted products — assert: state machine never orphans a position; exits retain queue priority
- **Clock/latency:** exchange-vs-local clock skew > 2s, signal→order latency 10× budget — assert: stale-context rejection engages; latency SLO (signal evaluation ≤ 150ms p99, order submission ≤ 300ms p99) is measured and alerting
- A release cannot ship if any chaos scenario fails — enforced in CI.

## 15. Non-Goals (additions)

- No prediction of whale intent — we react to *footprints* (sweeps, absorption, OI/funding), never to narratives
- No social-media "whale alert" feeds as signal inputs (unverifiable, latency-dead)
- No martingale, no grid averaging into cascades — a falling knife is ridden with structure or not at all

---

## Final Insight (v2.5.1)

Retail trades the pattern. SMC trades the retail. The whale trades the liquidity both of them leave behind. v2.5 moves this system to the third seat: map where the stops sleep (§3), watch who is crowded (§4), enter where traps spring rather than where they are baited (§5), monetize sideways tape like a market maker (§5.5), and scream — never whisper — when the market falls (§6–7). Up, down, or flat: every regime has a page in the playbook, and an unexecutable opportunity is still a reported one. And as of v2.5.1: no page of that playbook trades real size until it has survived backtest, shadow, and micro-live in sequence (§12) — because a rule that reads right and a rule that is proven right are different assets, and only the second one compounds.
