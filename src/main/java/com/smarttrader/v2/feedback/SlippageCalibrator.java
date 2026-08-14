package com.smarttrader.v2.feedback;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smarttrader.v2.constants.FeedbackConstants;
import com.smarttrader.v2.event.ConfigChangedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.ConfigChangeRecord;
import com.smarttrader.v2.model.TradeOutcome;
import com.smarttrader.v2.validation.TradeOutcomeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Daily slippage recalibration, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5.1.
 * Runs @Async per the testing gate ("non-blocking: all Phase 5 jobs run async, don't
 * block live trading") - @EnableAsync is on SmartTraderV2Application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlippageCalibrator {

    private final TradeOutcomeRepository tradeOutcomeRepository;
    private final SlippageModelService slippageModelService;
    private final ConfigChangeRepository configChangeRepository;
    private final TradingEventPublisher eventPublisher;

    @Value("${smart-trader.v2_5.tracked-symbols:}")
    private List<String> trackedSymbols;

    @Async
    @Scheduled(cron = "0 0 0 * * *")
    public void calibrateSlippage() {
        for (String symbol : trackedSymbols) {
            calibrateSymbol(symbol);
        }
    }

    private void calibrateSymbol(String symbol) {
        List<TradeOutcome> recent = tradeOutcomeRepository.findTop100BySymbolOrderByClosedAtDesc(symbol);
        if (recent.isEmpty()) {
            return;
        }

        double realizedSlippage = computeWeightedAverageSlippage(recent);
        double modeledSlippage = slippageModelService.getModeledSlippage(symbol);

        if (realizedSlippage > modeledSlippage * FeedbackConstants.SLIPPAGE_ALERT_MULTIPLE) {
            slippageModelService.updateSlippageModel(symbol, realizedSlippage);
            recordChange(symbol, modeledSlippage, realizedSlippage);
            log.info("slippageCalibrator symbol={} realized={} modeled={} action=model updated",
                    symbol, realizedSlippage, modeledSlippage);
        }
    }

    /** Exponential weighting, most-recent-first, half-life = SLIPPAGE_HALF_LIFE_FILLS. */
    private double computeWeightedAverageSlippage(List<TradeOutcome> mostRecentFirst) {
        double weightedSum = 0;
        double weightTotal = 0;
        for (int i = 0; i < mostRecentFirst.size(); i++) {
            double weight = Math.pow(0.5, i / FeedbackConstants.SLIPPAGE_HALF_LIFE_FILLS);
            weightedSum += mostRecentFirst.get(i).getSlippageMultiple() * weight;
            weightTotal += weight;
        }
        return weightTotal == 0 ? 0 : weightedSum / weightTotal;
    }

    private void recordChange(String symbol, double oldValue, double newValue) {
        Instant now = Instant.now();
        ConfigChangeRecord record = ConfigChangeRecord.builder()
                .configKey("slippage_model")
                .symbol(symbol)
                .oldValue(String.valueOf(oldValue))
                .newValue(String.valueOf(newValue))
                .reason("realized slippage exceeded " + FeedbackConstants.SLIPPAGE_ALERT_MULTIPLE + "x modeled")
                .changedAtNs(System.nanoTime())
                .changedAt(now)
                .build();
        configChangeRepository.save(record);

        ConfigChangedEvent event = new ConfigChangedEvent();
        event.symbol = symbol;
        event.configKey = "slippage_model";
        event.oldValue = String.valueOf(oldValue);
        event.newValue = String.valueOf(newValue);
        eventPublisher.publish(event);
    }
}
