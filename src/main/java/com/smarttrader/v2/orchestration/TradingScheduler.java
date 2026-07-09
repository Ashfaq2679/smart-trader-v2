package com.smarttrader.v2.orchestration;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.constants.TradingConstants;
import com.smarttrader.v2.context.AnalysisContextBuilder;
import com.smarttrader.v2.engine.TradeEngine;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configurable scheduler: on a fixed delay, pulls live candles for each configured
 * product, builds an AnalysisContext, asks TradeEngine to decide long/short/hold, and
 * (if approved) executes the decision via DecisionExecutionService.
 *
 * The whole bean is conditional on trading.scheduler.enabled=true (@ConditionalOnProperty,
 * not just an internal if-check) so it's simply absent from the application context - and
 * therefore @Scheduled truly can't fire - unless explicitly turned on. See
 * SchedulerProperties for full configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SchedulerProperties.class)
@ConditionalOnProperty(prefix = "trading.scheduler", name = "enabled", havingValue = "true")
public class TradingScheduler {

    private final ProductService productService;
    private final AnalysisContextBuilder analysisContextBuilder;
    private final TradeEngine tradeEngine;
    private final DecisionExecutionService decisionExecutionService;
    private final SchedulerProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${trading.scheduler.fixed-delay-ms:60000}")
    public void run() {
        Granularity granularity;
        try {
            granularity = Granularity.valueOf(properties.granularity());
        } catch (IllegalArgumentException e) {
            log.error("scheduler misconfigured: unknown granularity '{}', skipping this run", properties.granularity());
            return;
        }

        List<String> productIds = productService.findProductIdToProcess();
        if (productIds.isEmpty()) {
            log.warn("scheduler enabled but no products to process (check the products collection / candles.ignore.names), nothing to do");
            return;
        }

        for (String productId : productIds) {
            try {
                processProduct(productId, granularity);
            } catch (Exception e) {
                log.error("scheduler error processing productId={}: {}", productId, e.getMessage(), e);
            }
        }
    }

    private void processProduct(String productId, Granularity granularity) {
        List<Candle> candles = productService.getLiveCandles(productId, granularity);
        if (candles.size() < AnalysisContextBuilder.MIN_CANDLES) {
            log.warn("scheduler productId={} has only {} candles (need {}), skipping",
                    productId, candles.size(), AnalysisContextBuilder.MIN_CANDLES);
            return;
        }

        Instant now = Instant.now(clock);
        AnalysisContext ctx = analysisContextBuilder.build(candles, now);
        String correlationId = UUID.randomUUID().toString();

        TradeDecision decision = tradeEngine.decide(ctx, productId, properties.capital(),
                properties.riskPercent(), TradingConstants.DEFAULT_FEES, TradingConstants.DEFAULT_SLIPPAGE, correlationId);

        if (!decision.approved()) {
            log.info("scheduler productId={} correlationId={} decision=HOLD regime={} reason={}",
                    productId, correlationId, decision.regime(), decision.reason());
            return;
        }

        log.info("scheduler productId={} correlationId={} decision={} regime={} positionSize={}",
                productId, correlationId, decision.signal().direction(), decision.regime(), decision.positionSize());
        decisionExecutionService.execute(decision, productId, properties.userId(), ctx.price(), correlationId);
    }
}
