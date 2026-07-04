# SmartTrader Skills Specification (Production-Grade)

## Purpose
Defines reusable, deterministic, and testable capabilities (“Skills”) used by SmartTrader V2.
Skills must be stateless, composable, and independently testable.

---

## Design Principles

1. Pure functions (no side effects)
2. Deterministic outputs for same inputs
3. No external dependencies (e.g., TradingView)
4. Reusable across strategies
5. O(N) performance or better

---

## Skill Categories

### 1. Market Structure Skills
- detectSupportResistance
- detectTrend
- detectBreakout
- detectContinuation

---

## detectBreakout

Input:
- price
- nearestSupport
- nearestResistance
- strongCandle
- volumeSpike

Logic:
- price > resistance AND strongCandle AND volumeSpike → bullish breakout
- price < support AND strongCandle AND volumeSpike → bearish breakout

Output:
- BreakoutResult { direction, valid }

---

## detectContinuation

Input:
- recentBreakout
- isAboveEMA
- consolidationRange

Logic:
- recentBreakout == true
- price above EMA
- consolidationRange < threshold

Output:
- boolean

---

### 2. Volume Skills

## detectVolumeSpike

Input:
- candles
- lookback = 20
- multiplier = 1.8

Logic:
avgVolume = mean(last 20 candles)
currentVolume > avgVolume * multiplier

Output:
- boolean

---

### 3. Candle Skills

## detectStrongCandle

Input:
- open, close, high, low

Logic:
body = abs(close - open)
range = high - low
body / range >= 0.6

Output:
- boolean

---

### 4. Risk Skills

## calculateRiskReward

Input:
- entry
- stop
- target

Output:
- rr = (target - entry) / (entry - stop)

---

## calculatePositionSize

Input:
- capital
- riskPercent
- stopDistance

Logic:
riskAmount = capital * riskPercent
positionSize = riskAmount / stopDistance

---

### 5. Regime Detection Skills

## detectMarketRegime

Input:
- trend
- breakout
- continuation
- atrSpike

Logic:
IF breakout → BREAKOUT
ELSE IF continuation → CONTINUATION
ELSE IF trend + pullback → PULLBACK
ELSE IF atrSpike + drop → PANIC
ELSE → DISTRIBUTION

Output:
- MarketRegime

---

## Performance Constraints

- No nested loops over candle list
- Use rolling calculations where possible
- Avoid repeated indicator computation

---

## Testing Requirements

Each skill must have:
- Unit tests
- Edge case coverage
- Deterministic outputs

---

## Non-Goals

- No UI logic
- No external API dependency
- No probabilistic models

---

## Key Insight

Skills are atomic building blocks.

Strategies compose skills.
Regimes select strategies.
