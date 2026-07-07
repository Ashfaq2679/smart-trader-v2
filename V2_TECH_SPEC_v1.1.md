# V2_TECH_SPEC.md (v2.1 - Institutional Grade)

## Purpose
Defines deterministic, production-grade implementation rules for SmartTrader V2 including execution realism, event-driven design, and recovery guarantees.

---

## 1. Core Data Model

### AnalysisContext
- price
- bidPrice
- askPrice
- spread
- ema50, ema9, ema21
- atr
- trendDirection
- trendStrength
- nearestSupport
- nearestResistance
- volumeSpike (20, 1.8)
- strongCandle (body/range >= 0.6)
- recentBreakout
- isAboveEMA
- consolidationRangePercent
- lastCandleCloseTime
- dataLatencyMs

---

## 2. Market Regime Detection

Each detection must return:

MarketRegimeResult:
- regime
- confidence (0–1)

### Pullback
- trend == UP
- price near EMA/support
- ATR stable

### Breakout
- price > resistance OR < support
- strongCandle
- volumeSpike

### Continuation
- recentBreakout
- price holds above EMA
- consolidationRange < threshold

---

## 3. Strategy Rules

Each strategy must define:
- entryPrice
- entryType (MARKET / LIMIT)
- validityWindow

### Pullback
Stop: support - ATR buffer  
Target: resistance  

### Breakout
Stop: ATR * 1.2  
Target: ATR * 3.0  

### Continuation
Stop: EMA or consolidation low  
Target: ATR extension  

### 3.1 Entry Validity Window

Each strategy must define a validity window during which the signal remains actionable.

Pullback Strategy:
- Valid for 2–4 candles

Breakout Strategy:
- Valid only on breakout candle OR next candle
- If no continuation → invalidate immediately

Continuation Strategy:
- Valid for 1–3 candles after breakout

Additional Rules:
- If price deviates > 0.5 ATR from entry → invalidate
- If opposite signal appears → invalidate
- If volume drops significantly → invalidate breakout signals

Adaptive Adjustment:
- High volatility (ATR spike) → reduce validity window
- Low volatility → extend validity window slightly

---

## 4. Risk Rules

Adjusted R:R must include:

effectiveReward = target - entry - fees - slippage  
effectiveRisk   = entry - stop + fees + slippage  

Minimum R:R = 2.0

---

## 5. Decision Flow

1. Build AnalysisContext  
2. Detect MarketRegime  
3. Select Strategy  
4. Evaluate Trade  
5. Apply Risk Filters  
6. Global Risk Check  
7. Return Signal  

---

## 6. Order Execution Realism

- Include slippage tolerance
- Cancel if slippage > threshold
- Order timeout enforced
- Idempotency keys required

---

## 7. Position Service Enhancements

- Unrealized loss guard (1.5x risk → force exit)
- Partial fills supported
- State transitions enforced

---

## 8. Portfolio Risk Controls

- Dynamic correlation matrix (rolling)
- Exposure auto-adjustment
- Adaptive position sizing

---

## 9. Event Model (MANDATORY)

Events:
- CandleUpdated
- RegimeDetected
- SignalGenerated
- OrderPlaced
- OrderFilled
- PositionOpened
- PositionClosed
- PortfolioUpdated

Each event must:
- be idempotent
- include timestamp
- include correlationId

---

## 10. Data Integrity

- Reject stale data
- Ensure candle sequence continuity
- Validate price gaps

---

## 11. Recovery & Replay

- Replay last N events
- Rebuild positions from DB
- Resume without duplicate orders

---

## 12. Backtesting Compatibility

- Deterministic mode
- Historical replay
- Simulated fills

---

## 13. Execution Constraints

- O(N) computation only
- Rolling indicators
- No redundant recalculation

---

## 14. Non-Goals

- No TradingView dependency
- No black-box indicators
- No UI logic in backend

---

## Final Insight

System must behave identically in:
- live trading
- replay mode
- backtesting
