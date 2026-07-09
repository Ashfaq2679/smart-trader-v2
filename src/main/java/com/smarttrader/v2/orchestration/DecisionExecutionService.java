package com.smarttrader.v2.orchestration;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.execution.OrderExecutionService;
import com.smarttrader.v2.execution.OrderResult;
import com.smarttrader.v2.execution.OrderStatus;
import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.OrderRequest;
import com.smarttrader.v2.model.OrderResponse;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.position.PositionService;
import com.smarttrader.v2.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The missing wiring between TradeEngine and OrderService. A TradeEngine decision alone
 * isn't enough to touch real money: this layers in the section 6 realism checks
 * (OrderExecutionService) before ever calling the real Coinbase-backed OrderService, and
 * tracks the resulting position (PositionService) once an order actually goes through.
 *
 * Flow: TradeDecision -> OrderExecutionService.place() [slippage/timeout/idempotency] ->
 *       (if PLACED) OrderService.placeOrder() [real Coinbase order] ->
 *       (if successful) PositionService.open() [local lifecycle tracking]
 *
 * A decision that isn't approved (HOLD) or fails the realism check never reaches
 * OrderService, so no real order is placed for it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionExecutionService {

    private final OrderExecutionService orderExecutionService;
    private final OrderService orderService;
    private final PositionService positionService;
    private final Clock clock;

    /**
     * @param decision          the TradeEngine output for this productId
     * @param productId         exchange product identifier, e.g. "BTC-USD"
     * @param userId            the Coinbase-credentialed user to place the order for
     * @param currentQuotePrice latest price used for the realism/slippage check
     * @param correlationId     same correlationId TradeEngine used, so every event this
     *                          decision produces (regime/signal/order/position) can be
     *                          traced back to one another
     * @return the OrderResponse if an order was actually submitted; empty if the decision
     *         was a HOLD or didn't pass the realism check
     */
    public Optional<OrderResponse> execute(TradeDecision decision, String productId, String userId,
                                            double currentQuotePrice, String correlationId) {
        if (!decision.approved()) {
            log.info("decisionExecution productId={} correlationId={} decision=HOLD reason={}",
                    productId, correlationId, decision.reason());
            return Optional.empty();
        }

        Instant now = Instant.now(clock);
        OrderResult realismResult = orderExecutionService.place(decision, productId, currentQuotePrice,
                now, now, correlationId, correlationId);

        if (realismResult.status() != OrderStatus.PLACED) {
            log.info("decisionExecution productId={} correlationId={} not submitted, realism status={} reason={}",
                    productId, correlationId, realismResult.status(), realismResult.reason());
            return Optional.empty();
        }

        OrderRequest orderRequest = toOrderRequest(decision, productId, correlationId);
        OrderResponse response = orderService.placeOrder(userId, orderRequest);

        if (response.isSuccess()) {
            String positionId = response.getCoinbaseOrderId() != null ? response.getCoinbaseOrderId() : correlationId;
            positionService.open(decision, productId, positionId, now, correlationId);
            log.info("decisionExecution productId={} correlationId={} order placed coinbaseOrderId={}",
                    productId, correlationId, response.getCoinbaseOrderId());
        } else {
            log.warn("decisionExecution productId={} correlationId={} order placement failed: {}",
                    productId, correlationId, response.getFailureReason());
        }

        return Optional.of(response);
    }

    private OrderRequest toOrderRequest(TradeDecision decision, String productId, String correlationId) {
        SignalResult signal = decision.signal();
        boolean isLimit = signal.entryType() == EntryType.LIMIT;

        return OrderRequest.builder()
                .productId(productId)
                .side(signal.direction() == TradeDirection.LONG ? "BUY" : "SELL")
                .orderType(signal.entryType().name())
                .baseSize(decision.positionSize())
                .limitPrice(isLimit ? signal.entry() : null)
                .stopLoss(signal.stop())
                .takeProfit(signal.target())
                .entryPriceNum(signal.entry())
                .decisionFactors(Map.of(
                        "regime", decision.regime().name(),
                        "regimeConfidence", decision.regimeConfidence(),
                        "effectiveRiskReward", decision.effectiveRiskReward(),
                        "strategy", signal.strategyName()))
                .comments("correlationId=" + correlationId)
                .build();
    }
}
