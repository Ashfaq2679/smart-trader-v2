# V2_TECH_SPEC.md (v2.2 — Volatility Capture Edition)

Supersedes v2.1. Optimized for **short-lived crypto volatility capture on 1m–15m timeframes**.
Design premise: crypto volatility arrives in bursts, lasts minutes, and dies fast. The system's edge is (a) detecting compression before expansion, (b) entering in the first phase of expansion, (c) exiting before vol collapses. Everything else is risk containment.

---

# PART A — Critique of v2.1 (Gap Analysis)

| # | Gap in v2.1 | Why it loses money | Fix in v2.2 |
|---|---|---|---|
| 1 | No timeframe defined anywhere (ATR of what? EMA50 of what?) | Non-deterministic spec; untestable | §1 Timeframe contract |
| 2 | Only 3 regimes, all long/trend biased. No RANGE/CHOP, no NO_TRADE, no short side | Breakout strategies bleed to death in chop (~60–70% of the time); half the vol (downside) is ignored | §3 five regimes, bidirectional |
| 3 | No volatility state machine. "Capture volatility" but no compression/expansion/exhaustion model | You enter after the move; short-lived vol is over by then | §2 Volatility Engine (core of v2.2) |
| 4 | Fixed absolute thresholds (volumeSpike 1.8, body 0.6, ATR×1.2/×3.0) | BTC ≠ low-cap alt; thresholds decay; overfits one regime | Percentile-based adaptive thresholds, per symbol |
| 5 | Static stop/target only. No time-stop, no trailing, no partials, no vol-collapse exit | Short-lived moves round-trip; giving back the burst is the #1 P&L killer in scalping | §5 Exit ladder |
| 6 | Risk = only R:R ≥ 2.0. No daily loss limit, kill switch, streak guard, exposure caps | One bad day erases a month; R:R 2.0 is also wrong for scalping (1.2–1.5 realistic at high win rate) | §6 Risk stack, regime-dependent R:R |
| 7 | Execution ignores maker/taker economics, spread filter, book depth, quote-chasing | Taker fees (Coinbase Adv. ~0.05–0.6%) + spread eat the entire scalp edge | §7 Execution micro-rules |
| 8 | No intrabar/closed-candle rule | Signals evaluated intrabar repaint → backtest lies | §8 Closed-candle contract |
| 9 | No session/event awareness | Vol clusters around US open, FOMC, CPI; weekend books are thin | §9 Calendar layer |
| 10 | No multi-timeframe bias | 1m longs into a 1h downtrend = fighting the tape | §4 MTF filter |
| 11 | Backtest section has no cost/latency/lookahead model, no metrics | "Profitable" backtests that die live | §11 Backtest realism |
| 12 | Qualitative words ("near", "stable", "significantly") in a spec claiming determinism | Untestable; every dev implements differently | All rules quantified below |
| 13 | Correlation matrix "rolling" with no window/threshold/action | Decorative feature | §6.4 quantified |
| 14 | No config externalization | Threshold changes require redeploys | §12 Config schema |
| 15 | No observability/decision audit | Cannot do post-trade forensics, cannot improve | §10 Decision snapshots |

---

# PART B — Specification v2.2

## 1. Timeframe Contract

- **Execution timeframe (ETF): 1m and 5m.** All entries/exits computed here.
- **Signal timeframe (STF): 5m and 15m.** Regime + volatility state computed here.
- **Bias timeframe (BTF): 1h.** Trend direction filter only.
- Every indicator in AnalysisContext is suffixed with its timeframe: `atr14_5m`, `ema21_1m`, etc. Unsuffixed values are a spec violation.

### AnalysisContext (revised)
All v2.1 fields, plus:

- `atrPercentile_5m` — rank of current ATR(14) vs trailing 30 days (0–100)
- `bbWidthPercentile_5m` — Bollinger(20,2) width percentile vs 30 days
- `realizedVol_1m` — stdev of 1m log returns, 60-bar window, annualized
- `volOfVol` — stdev of ATR(14) changes, 20-bar window
- `relVolume` — current bar volume / SMA(volume, 20) *(replaces boolean volumeSpike)*
- `spreadBps` — (ask − bid)/mid × 10,000
- `bookImbalance` — bid depth / (bid+ask depth) within ±0.25% of mid (from L2 snapshot)
- `barsSinceExpansionStart` — bars since volatility state entered EXPANSION
- `sessionTag` — ASIA / EU / US / OVERLAP / WEEKEND
- `eventBlackout` — boolean (§9)
- `higherTfTrend_1h` — UP / DOWN / FLAT
- `fundingRateProxy` / `perpBasisBps` — optional, spot-perp basis as sentiment proxy

Context build must be O(1) per bar via rolling windows. Reject context if `dataLatencyMs > 2000` (1m) / `5000` (5m).

---

## 2. Volatility Engine (NEW — core component)

A per-symbol, per-STF state machine. This is the primary trade gate; regimes and strategies subordinate to it.

### States and deterministic transitions

**COMPRESSION** (the setup)
- Enter: `bbWidthPercentile_5m < 25` AND `atrPercentile_5m < 30` for ≥ 6 consecutive 5m bars
- Bonus signal: NR7 (narrowest range of last 7 bars) inside compression

**EXPANSION** (the tradeable window)
- Enter from COMPRESSION: bar range > 1.5 × ATR(14) AND `relVolume ≥ 2.0`
- Enter from NORMAL: bar range > 2.0 × ATR(14) AND `relVolume ≥ 2.5` (higher bar — no compression spring)
- **Tradeable window: bars 1–6 of EXPANSION on 5m (bars 1–3 on 1m).** After `barsSinceExpansionStart > 6`, no new entries — chasing dead vol is prohibited.

**EXHAUSTION** (get out / stand aside)
- Enter: any of —
  - range of current bar < 0.5 × range of expansion trigger bar for 2 consecutive bars
  - `relVolume < 1.0` for 2 consecutive bars while in EXPANSION
  - wick ratio > 0.65 on a bar ≥ 1.5 × ATR (climax bar)
- Action: no new entries; tighten all trailing stops to 0.5 × ATR (§5)

**NORMAL** — none of the above. Only Pullback/Continuation entries permitted, at reduced size (§6.2).

Hysteresis: a state must hold ≥ 2 bars before re-transition (prevents flip-flop).

Output event: `VolatilityStateChanged { symbol, tf, from, to, triggerBarTs, metrics }`.

---

## 3. Market Regime Detection (revised)

Five regimes, **bidirectional** (every trending regime has LONG and SHORT variants — downside vol is faster and larger; a long-only vol-capture system forfeits >50% of opportunity):

| Regime | Deterministic condition (5m/15m) |
|---|---|
| TREND_UP / TREND_DOWN | EMA9 > EMA21 > EMA50 (or inverse) AND ADX(14) > 22 AND `higherTfTrend_1h` agrees or FLAT |
| BREAKOUT_UP / BREAKOUT_DOWN | Close beyond nearest S/R by ≥ 0.25 × ATR AND body/range ≥ 0.6 AND `relVolume ≥ 2.0` AND vol state = EXPANSION |
| RANGE | ADX(14) < 18 AND price within [support, resistance] band ≥ 20 bars AND band width ≥ 3 × ATR |
| CHOP (NO_TRADE) | ADX < 18 AND band width < 3 × ATR, OR `spreadBps > 8`, OR vol state = EXHAUSTION with no position |
| NEWS_SHOCK (NO_TRADE for 3 bars) | 1m bar range > 4 × ATR(14)_1m OR `spreadBps` > 3 × its 1h median |

`MarketRegimeResult { regime, direction, confidence 0–1 }`. Confidence = weighted score of condition margins (each condition contributes its normalized distance past threshold). Regime must persist 2 bars before strategies act on it (except NEWS_SHOCK, immediate).

---

## 4. Strategy Rules (revised)

All strategies: symmetric long/short. Entry permitted only if:
1. Volatility Engine gate passes (§2)
2. Regime + direction matches strategy
3. `higherTfTrend_1h` is not opposite to trade direction (FLAT allowed for BREAKOUT only)
4. `spreadBps ≤ 5` (1m entries) / `≤ 8` (5m entries)
5. `eventBlackout == false`
6. `bookImbalance` ≥ 0.55 for longs / ≤ 0.45 for shorts (skip check if L2 unavailable; log it)

### 4.1 Breakout (primary vol-capture strategy)
- Trigger: BREAKOUT regime + EXPANSION state, entry within tradeable window (§2)
- Entry: stop-market at trigger-bar extreme + 0.05 × ATR (do not enter on the signal print itself — confirm follow-through)
- Stop: trigger-bar opposite extreme ∓ 0.3 × ATR, capped at 1.2 × ATR
- Validity: trigger bar + 1. Then invalidate.

### 4.2 Compression-Break (NEW — highest expectancy for short-lived vol)
- Trigger: vol state COMPRESSION → EXPANSION transition; direction = expansion bar direction
- Entry: market on transition bar close, or limit at 38.2% retrace of trigger bar, valid 2 bars
- Stop: compression range opposite boundary, capped at 1.0 × ATR
- This strategy front-runs §4.1 — if both fire, take Compression-Break (earlier phase = better price).

### 4.3 Pullback
- Trigger: TREND regime, price touches EMA21 zone (±0.25 × ATR) with counter-trend bar count ≤ 4
- Entry: limit at zone, valid 3 bars
- Stop: beyond EMA50 or last swing ∓ 0.5 × ATR, whichever is nearer, cap 1.5 × ATR

### 4.4 Continuation
- Trigger: within 3 bars of a filled breakout on same symbol, consolidation range < 0.8 × ATR, holds beyond breakout level
- Entry: stop-market at consolidation extreme; valid 3 bars
- Stop: consolidation opposite extreme

### 4.5 Range-Fade (NEW — harvests vol inside RANGE regime)
- Trigger: RANGE regime, price within 0.3 × ATR of band edge, rejection bar (wick ratio ≥ 0.5)
- Entry: limit at edge; Stop: 0.7 × ATR beyond band; Target: mid-band (TP1) and far band (TP2)
- Disabled when vol state = EXPANSION (never fade an expansion).

### 4.6 Universal invalidation (all strategies)
- Price moves > 0.5 × ATR beyond intended entry before fill → cancel
- Opposite regime/direction signal → cancel
- Vol state → EXHAUSTION before fill → cancel
- Validity windows above shrink ×0.5 (round down, min 1) when `atrPercentile > 80`; extend +1 bar when < 40.

---

## 5. Exit Ladder (NEW — replaces static stop/target)

Static targets forfeit short-lived vol. Deterministic ladder, in priority order:

1. **Hard stop** — per strategy above. Never widened. Ever.
2. **TP1 at +1.0R: close 50%, move stop to breakeven + fees.** The trade is now risk-free; the burst is banked.
3. **Runner (remaining 50%):** Chandelier trail at highest-high(since entry) − 2.2 × ATR (breakout/compression-break) or − 1.6 × ATR (pullback/continuation). Inverse for shorts.
4. **Vol-collapse exit:** vol state → EXHAUSTION → tighten trail to 0.5 × ATR immediately.
5. **Time stop:** not at +0.5R within 6 ETF bars (breakout/compression) or 10 bars (pullback) → exit at market. If the vol thesis hasn't paid quickly, it's wrong.
6. **Session guard:** flatten runners if `sessionTag` transitions into WEEKEND and unrealized < +1R.

Unrealized loss guard retained: mark-to-market loss ≥ 1.5 × initial risk (gap-through) → force exit + `AnomalyDetected` event.

---

## 6. Risk Stack (revised)

### 6.1 Per-trade
- `effectiveReward = TP1 − entry − 2×fee − expectedSlippage`; `effectiveRisk = entry − stop + 2×fee + expectedSlippage`
- `expectedSlippage = spreadBps/2 + impact`, impact = 0 if order notional < 10% of visible depth at ±0.1%, else skip trade
- **Minimum effective R:R (to TP1): 1.3 for Breakout/Compression-Break/Range-Fade; 1.8 for Pullback/Continuation.** (Flat 2.0 to a single target is mis-calibrated for scalping — win rate, not R multiple, carries the expectancy; the runner supplies the tail.)
- Fee model: Coinbase Advanced Trade maker/taker at the account's actual tier, injected via config — never hardcoded.

### 6.2 Position sizing (vol-targeted)
- `size = (equity × riskPct) / stopDistance`, riskPct base = 0.5%
- Scale by vol: ×0.5 if `atrPercentile > 85`; ×0.75 if 70–85; ×1.0 otherwise. High vol = smaller size, same dollar risk, wider effective coverage.
- Confidence scaling: ×(0.5 + 0.5 × regimeConfidence)
- Cap: order notional ≤ 5% of 24h volume/1440 × validity minutes (liquidity cap)

### 6.3 Account-level circuit breakers (kill switch hierarchy)
- Daily realized loss ≥ 2% equity → halt new entries until next UTC day
- 4 consecutive losers → halve riskPct; 6 → halt 4 hours
- Weekly loss ≥ 5% → full stop, manual re-arm required
- Max concurrent positions: 3; max per symbol: 1; max aggregate exposure: 15% equity
- `SystemHalted` event on every trigger; halts must survive restart (persisted)

### 6.4 Correlation control (quantified)
- Rolling 30-day Pearson on 1h returns, recomputed every 4h
- If |ρ| > 0.75 between an open position's symbol and a candidate: treat both as one exposure slot; combined risk ≤ 1× riskPct

---

## 7. Execution Micro-Rules (revised)

- **Order type policy:** Pullback/Range-Fade → post-only limit (maker). Breakout/Compression-Break → limit-through (marketable limit) at trigger ± slippage budget; never naked market orders.
- Slippage budget: 3 bps (BTC/ETH), 8 bps (majors), skip everything with median spread > 10 bps.
- Post-only chase: if unfilled and price ≤ 0.15 × ATR away, re-quote at best bid/ask max 3 times, then cancel.
- Order timeout: cancel unfilled entry orders at validity expiry or 90s, whichever first.
- Partial fills: ≥ 50% filled at timeout → keep, resize stop/TP to filled qty; < 50% → cancel remainder, manage or flatten per effective R:R recheck.
- Idempotency keys: `hash(symbol, strategy, triggerBarTs, direction)` — retained from v2.1, now with defined key.
- Market data: WebSocket (ticker + L2 + matches) primary; REST snapshot reconciliation every 60s; on WS gap > 5s → rebuild from REST, block entries until reconciled.
- Respect Coinbase rate limits with client-side token bucket; exits always have queue priority over entries.

---

## 8. Closed-Candle Contract (NEW)

- All signals evaluate on **closed candles only**. No intrabar signal evaluation, no repaint.
- Exception: stop-loss and NEWS_SHOCK detection run on tick/1s stream (protection may not wait for close).
- Candles built from trade stream; a candle is closed when first trade of next period arrives OR period end + 2s grace elapses.
- Out-of-order/duplicate trades: dedupe by trade_id, discard ticks older than current candle open.
- Sequence gap in candles → mark context STALE, no entries until backfilled from REST.

---

## 9. Calendar & Session Layer (NEW)

- `sessionTag` from UTC hour: ASIA 00–07, EU 07–13, US 13–21, OVERLAP EU/US 13–16, WEEKEND Sat/Sun
- Size multiplier: WEEKEND ×0.5 (thin books, fake breakouts); OVERLAP ×1.0
- `eventBlackout = true` for [T−15m, T+15m] around scheduled macro events (FOMC, CPI, NFP) from a config-loaded calendar; no new entries, existing positions switch to 0.5 × ATR trail
- Blackout is config data, not code.

---

## 10. Observability & Decision Audit (NEW)

- Persist a **DecisionSnapshot** for every evaluated signal (taken or rejected): full AnalysisContext, vol state, regime result, all filter outcomes with pass/fail, and rejection reason. Collection: `decision_snapshots`, TTL 90 days.
- Rejected-trade logging is mandatory — you cannot fix filters you cannot see.
- Metrics (Micrometer): signals/hour by strategy, fill ratio, avg slippage vs budget, time-in-trade, P&L by regime × strategy × session, vol-state distribution.
- Alerting: kill-switch triggers, WS gaps, slippage > 2× budget, data staleness.

## 11. Backtesting Realism (revised)

- Deterministic replay from persisted candles + L2 snapshots (retained; unchanged)
- Fill model: maker fills require price to *trade through* the limit; taker fills at bid/ask ± impact, never at mid, never at close
- Costs: actual fee tier + modeled slippage (§6.1) applied to every fill
- Latency simulation: configurable signal→order delay (default 250ms), order→exchange 100ms
- No-lookahead enforced structurally: strategies receive only closed bars (same code path as live — §8 guarantees this)
- Validation protocol: walk-forward (train 60d / test 30d, rolling), min 500 trades per strategy before live, parameter sensitivity ±20% must not flip sign of expectancy
- Report: expectancy/trade, profit factor, max DD, Sharpe & Sortino (on daily returns), win rate, avg win/loss, avg holding time, P&L split by regime/session/vol-state
- Live/replay/backtest behavioral identity retained as acceptance test: same input stream ⇒ byte-identical DecisionSnapshots.

## 12. Configuration Schema (NEW)

- All numeric thresholds in this spec live in `application-{env}.yml` under `smarttrader.v2.*`, overridable **per symbol** (`overrides.BTC-USD.…`)
- No threshold literal may appear in Java code (enforced via ArchUnit test)
- Config changes are events (`ConfigChanged`), snapshotted into DecisionSnapshots for audit

## 13. Event Model (extended)

v2.1 events retained, plus: `VolatilityStateChanged`, `SignalRejected`, `SystemHalted`, `AnomalyDetected`, `ConfigChanged`. All events: idempotent, timestamped (exchange time + local time), correlationId, and `schemaVersion`.

## 14. Non-Goals (retained + additions)

- No TradingView dependency, no black-box indicators, no UI logic in backend
- No martingale / averaging down — ever
- No mid-price fills in backtest
- No intrabar signal evaluation (except protective stops)

---

## Final Insight (revised)

Short-lived volatility is captured in three moments: **before** (compression detection), **during** (first 1–6 bars of expansion, bidirectional), and **out** (banking TP1 fast, trailing the rest, time-stopping the failures). Every rule above serves one of those three moments or protects the account while waiting for them. The system must still behave identically in live, replay, and backtest — §8 and §11 make that a structural property, not an aspiration.
