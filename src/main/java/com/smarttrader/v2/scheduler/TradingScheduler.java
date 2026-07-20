package com.smarttrader.v2.scheduler;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.context.AnalysisContextBuilder;
import com.smarttrader.v2.engine.TradeEngine;
import com.smarttrader.v2.execution.OrderService;
import com.smarttrader.v2.execution.PositionService;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.service.ProductService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls Coinbase for fresh candles on a fixed cadence per granularity and runs each
 * tracked symbol through the full pipeline: AnalysisContextBuilder -> TradeEngine ->
 * OrderService -> PositionService. This is the "analyze, then place market orders based
 * on the decision" entry point.
 *
 * Every granularity has its own independent enabled flag and interval, both configurable
 * in application.yml under smart-trader.scheduler.granularities.<name> - restart-time
 * configurable (a @Value field is read once at bean construction, same limitation as
 * every other @Scheduled job in this codebase, e.g. SlippageCalibrator's hardcoded cron).
 * smart-trader.scheduler.enabled is a single global kill switch on top of that: every
 * granularity defaults to disabled, and the global switch also defaults to disabled, so
 * this scheduler is opt-in twice over rather than firing against live Coinbase data the
 * moment the app starts.
 *
 * OrderService itself defaults to dry-run (smart-trader.execution.live-enabled=false) -
 * see its javadoc for how it raises a BOLD alert (ExecutionDegradedEvent) if live trading
 * is supposed to be on but a real order can't actually be placed.
 *
 * Wiring OpportunitySirenService/StrategySelector.selectStrategies() into this poll loop
 * remains a deferred integration, consistent with the deferral already documented on
 * TradeEngine itself (its constructor/tests are pinned).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingScheduler {

    private final AnalysisContextBuilder contextBuilder;
    private final TradeEngine tradeEngine;
    private final OrderService orderService;
    private final PositionService positionService;
    private final ProductService productService;

    @Value("${smart-trader.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Value("${smart-trader.v2_5.tracked-symbols}")
    private List<String> trackedSymbols;

    @Value("${smart-trader.scheduler.capital:10000}")
    private double capital;

    @Value("${smart-trader.scheduler.granularities.one-minute.enabled:false}")
    private boolean oneMinuteEnabled;

    @Value("${smart-trader.scheduler.granularities.five-minute.enabled:false}")
    private boolean fiveMinuteEnabled;

    @Value("${smart-trader.scheduler.granularities.fifteen-minute.enabled:false}")
    private boolean fifteenMinuteEnabled;

    @Value("${smart-trader.scheduler.granularities.thirty-minute.enabled:false}")
    private boolean thirtyMinuteEnabled;

    @Value("${smart-trader.scheduler.granularities.one-hour.enabled:false}")
    private boolean oneHourEnabled;

    @Value("${smart-trader.scheduler.granularities.four-hour.enabled:false}")
    private boolean fourHourEnabled;

    @Scheduled(fixedDelayString = "${smart-trader.scheduler.granularities.one-minute.interval-ms:60000}")
    public void pollOneMinute() {
        pollIfEnabled(Granularity.ONE_MINUTE, oneMinuteEnabled);
    }

    @Scheduled(fixedDelayString = "${smart-trader.scheduler.granularities.five-minute.interval-ms:300000}")
    public void pollFiveMinute() {
        pollIfEnabled(Granularity.FIVE_MINUTE, fiveMinuteEnabled);
    }

    @Scheduled(fixedDelayString = "${smart-trader.scheduler.granularities.fifteen-minute.interval-ms:900000}")
    public void pollFifteenMinute() {
        pollIfEnabled(Granularity.FIFTEEN_MINUTE, fifteenMinuteEnabled);
    }

    @Scheduled(fixedDelayString = "${smart-trader.scheduler.granularities.thirty-minute.interval-ms:1800000}")
    public void pollThirtyMinute() {
        pollIfEnabled(Granularity.THIRTY_MINUTE, thirtyMinuteEnabled);
    }

    @Scheduled(fixedDelayString = "${smart-trader.scheduler.granularities.one-hour.interval-ms:3600000}")
    public void pollOneHour() {
        pollIfEnabled(Granularity.ONE_HOUR, oneHourEnabled);
    }

    @Scheduled(fixedDelayString = "${smart-trader.scheduler.granularities.four-hour.interval-ms:14400000}")
    public void pollFourHour() {
        pollIfEnabled(Granularity.FOUR_HOUR, fourHourEnabled);
    }
    
    @PostConstruct
    public void startup() {
		if (this.trackedSymbols.isEmpty()) {
			trackedSymbols = productService.findProductIdToProcess();  //List.of("ZEC-USDC", "POL-USDC");
		} else {
			log.info("Scheduler will poll the following symbols: {}", this.trackedSymbols);
		}
	}

    private void pollIfEnabled(Granularity granularity, boolean granularityEnabled) {
        if (!schedulerEnabled || !granularityEnabled) {
            return;
        }
        for (String symbol : trackedSymbols) {
            pollSymbol(symbol, granularity);
        }
    }

    private void pollSymbol(String symbol, Granularity granularity) {
        try {
            AnalysisContext ctx = contextBuilder.build(symbol, granularity);
            TradeDecision decision = tradeEngine.decide(ctx, capital);
            log.info("scheduler symbol={} granularity={} approved={} reason={}",
                    symbol, granularity, decision.approved(), decision.reason());

            orderService.execute(decision, symbol, ctx)
                    .ifPresent(order -> positionService.onOrderPlaced(order, decision.signal()));
        } catch (Exception e) {
            log.error("scheduler symbol={} granularity={} failed to poll/decide/execute: {}",
                    symbol, granularity, e.getMessage(), e);
        }
    }
}
