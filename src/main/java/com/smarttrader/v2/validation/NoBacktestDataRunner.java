package com.smarttrader.v2.validation;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Default BacktestRunner while no real candle-replay backtest engine exists in this
 * codebase (see BacktestRunner's javadoc). Always reports "no evidence" so
 * ValidationPipelineService.validateResearch() never promotes a strategy on faked data;
 * this keeps the validation pipeline's plumbing (stage transitions, event publishing,
 * gating logic) exercisable and testable today without pretending backtesting exists.
 */
@Slf4j
@Component
public class NoBacktestDataRunner implements BacktestRunner {

    @Override
    public BacktestResult run(String strategyName, String symbol) {
        log.info("backtestRunner strategy={} symbol={} result=no-data reason=no candle-replay backtest engine wired up yet",
                strategyName, symbol);
        return BacktestResult.noData();
    }
}
