# V2_TECH_SPEC.md

## Purpose
Defines exact implementation rules for SmartTrader V2. No ambiguity allowed.

---

## 1. Core Data Model

### AnalysisContext
- price
- ema50, ema9, ema21
- atr
- trend (direction + strength)
- nearestSupport
- nearestResistance
- volumeSpike (lookback=20, multiplier>=1.8)
- strongCandle (body/range >= 0.6)
- recentBreakout
- isAboveEMA
- consolidationRangePercent

---

## 2. Market Regime Detection

### Pullback
- trend == UP
- price near EMA or support
- ATR stable

### Breakout
- price > resistance OR price < support
- strongCandle == true
- volumeSpike == true

### Continuation
- recentBreakout == true
- price above EMA
- small consolidation range

---

## 3. Strategy Rules

### Pullback Strategy
Entry:
- bullishLocation == true

Stop:
- support - ATR buffer

Target:
- resistance

---

### Breakout Strategy
Entry:
- validBreakoutUp

Stop:
- ATR * BREAKOUT_RISK_ATR

Target:
- ATR * BREAKOUT_REWARD_ATR

---

### Continuation Strategy
Entry:
- breakoutContinuation == true

Stop:
- EMA or consolidation low

Target:
- ATR or extension

---

## 4. Risk Rules

- Minimum R:R = 2.0
- Risk per trade = 1% capital

Position Size:
positionSize = riskCapital / (entry - stop)

---

## 5. Decision Flow

1. Build AnalysisContext
2. Detect MarketRegime
3. Select Strategy
4. Evaluate trade
5. Apply R:R filter
6. Return SignalResult
