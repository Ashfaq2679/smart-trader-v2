
# CLAUDE.md

## Project Identity

**Project:** Smart Trader V1

Smart Trader V1 is a production-grade algorithmic cryptocurrency trading platform built using Java 17, Spring Boot, MongoDB, Kafka (planned), and Coinbase Advanced Trade API.

The objective is **not** to build another indicator-based bot. The objective is to build an institutional-style decision engine that combines:

- Price Action
- Market Structure
- Volume
- Volatility
- Liquidity
- Sentiment
- Momentum
- Risk Management

Every future enhancement should move the project toward that goal.

---

# Core Engineering Principles

1. Price is the source of truth.
2. Indicators confirm price, never replace it.
3. Every algorithm must be independently testable.
4. Prefer composition over inheritance.
5. Keep business logic outside controllers.
6. Every strategy must implement TradingStrategy.
7. New indicators implement TechnicalIndicator.
8. Repository layer performs persistence only.
9. Services orchestrate workflows.
10. Strategies never call repositories directly.

---

# Preferred Technology Stack

- Java 17
- Spring Boot 3.x
- Maven
- MongoDB
- Caffeine
- Kafka
- Kafka Streams
- Docker
- JUnit5
- Mockito
- Lombok

---

# Architecture

Controller
    ↓
Service
    ↓
Strategy Engine
    ↓
Indicators
    ↓
Risk Manager
    ↓
Execution Layer

No shortcuts across layers.

---

# Trading Philosophy

Priority:

1. Market Structure
2. Price Action
3. Support & Resistance
4. Volume Expansion
5. Volatility Contraction (VCP)
6. Relative Strength
7. Sentiment
8. ATR
9. RSI
10. MACD

Never take trades solely because of RSI or MACD.

---

# Current Strategies

- PriceActionStrategy
- RSI
- RSI + SMA
- RSI Enhanced
- Volume Breakout
- VCP (planned)
- Momentum Score (planned)

---

# Planned Engines

## Momentum Engine

Inputs

- ATR contraction
- Bollinger squeeze
- Volume expansion
- Relative strength vs BTC
- Sentiment acceleration
- Open Interest
- Funding Rate
- VCP

Output

MomentumScore (0-100)

---

## Sentiment Engine

Collect data from:

- Twitter/X
- Reddit
- Coinbase News
- CoinDesk
- CoinTelegraph
- On-chain metrics

Publish normalized sentiment events to Kafka.

---

## Kafka Streams

Topics

- candles.1m
- candles.5m
- candles.15m
- candles.1h
- candles.4h

Derived topics

- trend.score
- momentum.score
- sentiment.score
- breakout.events
- trade.decisions

---

# CQRS Direction

Command Side

- Orders
- Credentials
- Preferences

Query Side

- Dashboard
- Market Scanner
- Portfolio
- Statistics

Use Transactional Outbox.

Never publish directly inside DB transaction.

---

# MongoDB

Collections

- users
- user_credentials
- preferences
- orders
- positions
- sentiment
- candles
- coins

Indexes must exist on frequently queried fields.

---

# Coding Standards

- Constructor injection only
- No field injection
- Immutable DTOs
- Small methods
- Maximum one responsibility per class
- Favor interfaces
- No duplicated calculations

---

# Logging

Use structured logging.

Log:

- strategy
- productId
- timeframe
- confidence
- execution time

Never log secrets.

---

# Testing

Every strategy requires:

- bullish tests
- bearish tests
- sideways tests
- edge cases

Target >90% coverage for strategy package.

---

# Risk Management

Never approve a trade without:

- Stop Loss
- Take Profit
- Position Size
- Risk Reward

Target R:R >= 1:2.

---

# Performance

Avoid O(N²).

Indicator calculations should reuse rolling windows whenever possible.

Prefer streaming calculations.

---

# AI Assistant Rules

When generating code:

- Produce production-ready code.
- Use SOLID.
- Prefer reusable components.
- Explain architectural decisions.
- Never hardcode credentials.
- Preserve existing public APIs.
- Consider performance.
- Consider concurrency.

When reviewing code:

- Suggest cleaner abstractions.
- Reduce duplication.
- Improve extensibility.
- Prefer event-driven solutions when appropriate.

---

# Long-term Roadmap

Phase 1
- Stable trading engine

Phase 2
- Kafka Streams
- Sentiment

Phase 3
- Portfolio manager
- Backtesting

Phase 4
- Machine learning feature scoring

Phase 5
- Multi-exchange support

The project should evolve toward an institutional-grade quantitative trading platform rather than a collection of isolated indicators.
