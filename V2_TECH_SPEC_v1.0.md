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

## 6. User Service

1. Application must support multiple users (multi-tenant).
2. Each user has:

   UserPreferences:
   - list of products (coin pairs)
   - execution interval (N minutes)
   - trading mode (AUTO / MANUAL)

   RiskConfig:
   - orderAmount (USD)
   - takeProfitPercent
   - stopLossPercent
   - riskPerTrade (default 1%)

3. Each user must have default preferences if not configured.
4. Users can view and update preferences.
5. Maintain available balance per user to prevent order failure.
6. Track open positions per user per product.

---

## 7. Product Service (Coin Service)

1. Runs every N minutes (configurable).
2. Read coins from MongoDB:

   database: coinbase_v2
   collection: coins

   schema:
   {
     "productId": "BTC-USDC",
     "coin": "BTC",
     "name": "Bitcoin"
   }

3. Fetch candles for all configured products.
4. Build AnalysisContext for each product.

---

## 8. Order Service

1. System is multi-user and multi-tenant.

2. For each user:
   - Apply user preferences (products, interval)
   - Build AnalysisContext
   - Evaluate decision using V2 Decision Flow

3. Place order ONLY if:
   - Signal is BUY or SELL
   - R:R >= minimum threshold
   - User has sufficient balance
   - No existing open position for the product

4. Order placement:
   - Use MARKET orders for breakout strategies
   - Use LIMIT orders for pullback strategies (optional)

5. If using LIMIT orders:
   - Ensure price is within valid exchange constraints

6. Use Coinbase API:
   CreateOrderRequest

7. Persist:
   - order details
   - position details

8. Asynchronously:
   - Track order status updates
   - Handle partial fills and rejections

9. Error Handling:
   - Retry failed API calls with backoff
   - Log all failures
   - Avoid duplicate order placement (idempotency)

10. Concurrency:
   - Ensure one active trade per user per product
   - Use locking or idempotent keys

## 9. Position Service

Purpose:
Manages the full lifecycle of a trade after order execution.

---

### 9.1 Position Creation

1. On successful order placement:
   - Create a Position record
   - Link to userId and productId
   - Store:
     - entryPrice
     - quantity
     - stopLossPrice
     - takeProfitPrice
     - strategyType (PULLBACK / BREAKOUT / CONTINUATION)
     - timestamp

---

### 9.2 Position State

Each position must have state:

- OPEN
- PARTIALLY_CLOSED
- CLOSED

---

### 9.3 Exit Logic

Position Service must monitor:

#### Stop Loss
- Trigger when price <= stopLossPrice (long)
- Trigger when price >= stopLossPrice (short)

#### Take Profit
- Trigger when price >= takeProfitPrice (long)
- Trigger when price <= takeProfitPrice (short)

---

### 9.4 Trailing Stop (optional but recommended)

For breakout/continuation trades:

1. When price moves +1R:
   - Move stopLoss to entryPrice (break-even)

2. When price moves +2R:
   - Trail stop using:
     - EMA21 OR
     - ATR-based trailing

---

### 9.5 Partial Exit (optional)

1. At +1R:
   - Close 50% position

2. Let remaining position run:
   - Managed by trailing stop

---

### 9.6 Position Monitoring

1. Runs asynchronously (every few seconds / via stream)
2. Uses latest price data
3. Evaluates exit conditions

---

### 9.7 Position Closure

On exit:

1. Place exit order via exchange API
2. Update position status to CLOSED
3. Record:
   - exitPrice
   - exitTime
   - realizedPnL

---

### 9.8 PnL Calculation

realizedPnL = (exitPrice - entryPrice) * quantity (long)

For short:
realizedPnL = (entryPrice - exitPrice) * quantity

---

### 9.9 Risk Enforcement

Before allowing new trades:

1. Check:
   - Max open positions per user
   - Total exposure per user
   - Max daily loss threshold

---

### 9.10 Conflict Prevention

- Only ONE active position per user per product
- Reject new entry if position already exists

---

### 9.11 Data Persistence

MongoDB collection: positions

Schema:

{
  "userId": "...",
  "productId": "BTC-USDC",
  "entryPrice": 65000,
  "quantity": 0.01,
  "stopLoss": 63000,
  "takeProfit": 69000,
  "status": "OPEN",
  "strategy": "BREAKOUT",
  "createdAt": "...",
  "updatedAt": "...",
  "exitPrice": null,
  "pnl": null
}

---

### 9.12 Error Handling

- Retry failed exit orders
- Handle partial fills
- Ensure idempotent updates

---

### 9.13 Concurrency

- Lock position during update
- Avoid duplicate exits
- Ensure consistent state transitions

## 10. Portfolio & Risk Management Service

1. Purpose:
   Manages overall capital allocation, exposure, and risk across all trades.
   Ensures capital protection and controlled drawdowns.

---

2. Portfolio State (per user)

   Track:
   - totalCapital
   - availableCapital
   - usedCapital
   - unrealizedPnL
   - realizedPnL
   - dailyPnL
   - maxDrawdown

---

3. Exposure Rules

3.1 Max capital per trade:
   - Default: 5% of total capital

3.2 Max concurrent positions:
   - Default: 5–10 positions per user

3.3 Max exposure per product:
   - Default: 10% per coin

3.4 Correlated exposure limit:
   - Example:
     BTC, ETH, SOL → grouped
     Total exposure limit: 30%

---

4. Risk Limits (Critical)

4.1 Max risk per trade:
   - Default: 1% of total capital

4.2 Max daily loss:
   - Default: 3% of total capital
   - If exceeded:
     → Stop all new trades for the day

4.3 Max drawdown:
   - Default: 10–15%
   - If exceeded:
     → Reduce position size OR pause trading

4.4 Consecutive losses:
   - If 3–5 losses occur:
     → Reduce risk per trade by 50%

---

5. Position Sizing

5.1 Formula:
   positionSize = riskAmount / stopDistance

   Where:
   riskAmount = totalCapital * riskPerTrade

5.2 Adjustments:

   - High volatility (ATR spike):
     → Reduce position size by 30–50%

   - Strong signal (high confidence):
     → Allow full position size

   - Weak signal:
     → Reduce position size by 50%

---

6. Capital Allocation Strategy

6.1 Allocate capital based on strategy type:

   - Breakout: 6% capital
   - Continuation: 5% capital
   - Pullback: 4% capital

6.2 Ensure total allocated capital does not exceed limits

---

7. Trade Approval Gate (Mandatory)

Before placing any trade:

   - Check available capital
   - Check exposure limits
   - Check daily loss threshold
   - Check max open positions
   - Check correlated exposure

Only allow trade if ALL checks pass.

---

8. Portfolio-Level Protection

8.1 If total portfolio loss exceeds threshold:
   - Option 1: Stop new trades
   - Option 2: Close all open positions (aggressive mode)

---

9. PnL Tracking

Track:
   - Realized PnL
   - Unrealized PnL
   - Daily PnL
   - Weekly PnL

Update on:
   - Price updates OR
   - Scheduled intervals

---

10. Performance Metrics

Track:
   - Win rate
   - Average R:R
   - Profit factor
   - Max drawdown

10.1 Expectancy:

   Expectancy = (winRate × avgWin) − (lossRate × avgLoss)

---

11. Adaptive Risk Control

11.1 If system performs well:
   → Gradually increase position size

11.2 If system performs poorly:
   → Reduce position size

Example:
   - Win streak: +10% size
   - Loss streak: −30% size

---

12. Data Persistence

MongoDB collection: portfolio

Schema:

{
  "userId": "...",
  "totalCapital": 10000,
  "availableCapital": 7000,
  "usedCapital": 3000,
  "dailyPnL": -150,
  "maxDrawdown": -800,
  "updatedAt": "..."
}

---

13. Concurrency & Safety

   - Lock portfolio during updates
   - Prevent race conditions
   - Ensure atomic updates

---

14. Integration Points

Portfolio Service interacts with:

   - Order Service (before trade placement)
   - Position Service (after execution)
   - Risk Engine (position sizing)