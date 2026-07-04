# SmartTrader V2 - Production Technical Specification

## 1. Objective
Build a deterministic, regime-aware trading engine capable of adapting to crypto market conditions with high reliability, testability, and performance.

---

## 2. System Pipeline

Market Data → Context Builder → MarketRegimeDetector → StrategySelector → Strategy → Risk Engine → Execution

---

## 3. Core Data Model

### AnalysisContext
Required Fields:
- price
- ema9, ema21, ema50
- atr
- trendDirection (UP, DOWN, SIDEWAYS)
- trendStrength
- nearestSupport
- nearestResistance
- volumeSpike (20 lookback, multiplier 1.8)
- strongCandle (body/range >= 0.6)
- isAboveEMA
- recentBreakout (last 2–3 candles)
- atrSpike

---

## 4. Market Regime Detection Rules

### Pullback
- trendDirection == UP
- price near EMA50 OR support
- atrSpike == false

### Breakout
- price > resistance OR price < support
- strongCandle == true
- volumeSpike == true

### Continuation
- recentBreakout == true
- price holds above EMA50
- consolidation range < 2%

### Distribution
- trend weakening
- repeated resistance rejection
- volume divergence

### Panic
- large red candles
- ATR spike
- breakdown below support

---

## 5. Strategy Logic

### Pullback Strategy
Entry:
- bullishLocation true

Stop:
- support - ATR * 0.5

Target:
- resistance

---

### Breakout Strategy
Entry:
- breakout + strong candle + volume spike

Stop:
- ATR * 1.2

Target:
- ATR * 3.0

---

### Continuation Strategy
Entry:
- post-breakout consolidation

Stop:
- EMA50 OR consolidation low

Target:
- ATR extension OR previous measured move

---

## 6. Risk Engine

### Risk Rules
- Max risk per trade = 1% capital
- Minimum R:R = 2.0

### Position Sizing
positionSize = riskCapital / riskPerUnit

---

## 7. Execution Rules

- Use market orders for breakouts
- Use limit orders for pullbacks
- Slippage tolerance: configurable
- Partial exits allowed (optional)

---

## 8. State Management

Track:
- Open positions
- Last detected regime
- Last breakout event

---

## 9. Logging Requirements

Log:
- regime
- strategy
- rr
- score
- entry/exit price

---

## 10. Testing Requirements

Each strategy must include:
- bullish scenario
- bearish scenario
- sideways scenario
- edge cases

---

## 11. Performance Constraints

- O(N) processing only
- Avoid recalculating indicators
- Use rolling windows

---

## 12. Non-Goals

- No reliance on external indicator APIs (e.g., TradingView)
- No subjective drawing tools

---

## 13. Future Extensions

- Sentiment Engine
- ML scoring
- Portfolio optimization
