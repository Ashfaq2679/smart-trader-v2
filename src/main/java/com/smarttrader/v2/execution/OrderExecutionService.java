package com.smarttrader.v2.execution;

import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Order Execution Realism, per V2_TECH_SPEC_v1.1.md section 6:
 * - Include slippage tolerance
 * - Cancel if slippage > threshold
 * - Order timeout enforced
 * - Idempotency keys required
 *
 * This sits at the Execution Layer of the architecture (Controller -> Service ->
 * Strategy Engine -> Indicators -> Risk Manager -> Execution Layer): it consumes the
 * already-approved TradeDecision from RiskEngine/GlobalRiskCheck and decides whether the
 * order can actually be placed against current market conditions. Submitting the order to
 * the exchange itself (the authenticated Coinbase call) is out of scope here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExecutionService {

    private final IdempotencyKeyStore idempotencyKeyStore;

    /**
     * @param decision           the approved (or rejected) trade decision to execute
     * @param productId          exchange product identifier, e.g. "BTC-USD"
     * @param currentQuotePrice  latest quoted price at the moment of execution
     * @param signalGeneratedAt  when the strategy produced the signal (start of its validity window)
     * @param now                current time, used to enforce the order timeout
     * @param idempotencyKey     unique key for this submission attempt; required, retries with
     *                           the same key return the original result instead of re-executing
     */
    public OrderResult place(TradeDecision decision, String productId, double currentQuotePrice,
                              Instant signalGeneratedAt, Instant now, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }

        Optional<OrderResult> existing = idempotencyKeyStore.find(idempotencyKey);
        if (existing.isPresent()) {
            log.info("orderExecution idempotencyKey={} productId={} replay status={}",
                    idempotencyKey, productId, existing.get().status());
            return existing.get();
        }

        OrderResult result = evaluate(decision, productId, currentQuotePrice, signalGeneratedAt, now, idempotencyKey);
        idempotencyKeyStore.save(idempotencyKey, result);

        log.info("orderExecution idempotencyKey={} productId={} status={} slippage={} reason={}",
                idempotencyKey, productId, result.status(), result.slippage(), result.reason());
        return result;
    }

    private OrderResult evaluate(TradeDecision decision, String productId, double currentQuotePrice,
                                  Instant signalGeneratedAt, Instant now, String idempotencyKey) {
        if (!decision.approved()) {
            return result(idempotencyKey, productId, decision, OrderStatus.REJECTED,
                    "trade decision was not approved", currentQuotePrice, 0, now);
        }

        SignalResult signal = decision.signal();
        Instant expiresAt = signalGeneratedAt.plus(signal.validityWindow());
        if (now.isAfter(expiresAt)) {
            return result(idempotencyKey, productId, decision, OrderStatus.EXPIRED,
                    "signal validity window expired at " + expiresAt, currentQuotePrice, 0, now);
        }

        double slippage = Math.abs(currentQuotePrice - signal.entry()) / signal.entry();
        if (slippage > TradingConstants.SLIPPAGE_TOLERANCE) {
            return result(idempotencyKey, productId, decision, OrderStatus.CANCELLED,
                    "slippage %.4f exceeds tolerance %.4f".formatted(slippage, TradingConstants.SLIPPAGE_TOLERANCE),
                    currentQuotePrice, slippage, now);
        }

        return result(idempotencyKey, productId, decision, OrderStatus.PLACED, "placed",
                currentQuotePrice, slippage, now);
    }

    private OrderResult result(String idempotencyKey, String productId, TradeDecision decision, OrderStatus status,
                                String reason, double currentQuotePrice, double slippage, Instant now) {
        SignalResult signal = decision.signal();
        return OrderResult.builder()
                .idempotencyKey(idempotencyKey)
                .productId(productId)
                .status(status)
                .reason(reason)
                .direction(signal.direction())
                .requestedPrice(signal.entry())
                .quotedPrice(currentQuotePrice)
                .slippage(slippage)
                .positionSize(decision.positionSize())
                .evaluatedAt(now)
                .build();
    }
}
