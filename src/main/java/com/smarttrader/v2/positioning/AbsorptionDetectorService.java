package com.smarttrader.v2.positioning;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.constants.PositioningConstants;
import com.smarttrader.v2.event.AbsorptionDetectedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects absorption on candle close, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md section
 * 1B.4 / V2_TECH_SPEC_v2.5.md section 4: ">= 2.5x relative volume sell-taker flow while
 * price declines < 0.25 x ATR" (someone big is buying the panic), and the mirror on the
 * ask side (someone big is selling the rip).
 *
 * relativeVolume is the same rough wick/close proxy the plan's own snippet uses (no L2 or
 * matches-stream taker-side breakdown consumed here yet - that's a finer-grained input
 * than Phase 1B wires up) - candle volume weighted by how little of it moved price.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbsorptionDetectorService {

    private final TradingEventPublisher eventPublisher;

    public void onCandleClose(String symbol, Candle candle, AnalysisContext ctx) {
        if (ctx.atr() <= 0 || candle.open() <= 0 || candle.close() <= 0) {
            return;
        }

        if (candle.close() < candle.open()) {
            checkAbsorption(symbol, candle, ctx, "BID",
                    candle.volume() * (1.0 - candle.close() / candle.open()),
                    (candle.open() - candle.close()) / ctx.atr());
        } else if (candle.close() > candle.open()) {
            checkAbsorption(symbol, candle, ctx, "ASK",
                    candle.volume() * (1.0 - candle.open() / candle.close()),
                    (candle.close() - candle.open()) / ctx.atr());
        }
    }

    private void checkAbsorption(String symbol, Candle candle, AnalysisContext ctx, String side,
                                  double relativeVolume, double priceMoveAtr) {
        if (relativeVolume < PositioningConstants.ABSORPTION_RELATIVE_VOLUME_THRESHOLD
                || priceMoveAtr >= PositioningConstants.ABSORPTION_MAX_PRICE_DECLINE_ATR) {
            return;
        }

        AbsorptionDetectedEvent event = new AbsorptionDetectedEvent();
        event.symbol = symbol;
        event.side = side;
        event.relativeVolume = relativeVolume;
        event.priceDeclineAtr = priceMoveAtr;

        log.info("absorption symbol={} side={} relativeVolume={} priceMoveAtr={}",
                symbol, side, relativeVolume, priceMoveAtr);
        eventPublisher.publish(event);
    }
}
