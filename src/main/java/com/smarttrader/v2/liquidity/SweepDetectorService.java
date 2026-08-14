package com.smarttrader.v2.liquidity;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.event.LiquiditySweepDetectedEvent;
import com.smarttrader.v2.event.TradingEventPublisher;
import com.smarttrader.v2.model.AnalysisContext;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.LiquidityMap;
import com.smarttrader.v2.model.LiquidityPool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects liquidity sweeps on candle close, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md
 * section 1A.2 / V2_TECH_SPEC_v2.5.md section 3, rule 3: "trade beyond it, followed by
 * close back inside within 2 bars". Phase 1A checks the same closed bar (wick beyond,
 * close back inside); true 2-bar reclaim tracking needs state across candles and belongs
 * with the Sweep-and-Reclaim strategy itself (Phase 2), which also applies the
 * density >= 50 / relVolume >= 1.5 confluence gates from section 5.2 - this service only
 * emits the raw LiquiditySweepDetected signal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SweepDetectorService {

    private final LiquidityMapperService liquidityMapper;
    private final TradingEventPublisher eventPublisher;

    public void onCandleClose(String symbol, Candle candle, AnalysisContext ctx) {
        LiquidityMap map = liquidityMapper.mapLiquidity(symbol, ctx);

        for (LiquidityPool pool : map.pools()) {
            if (isSweep(candle, pool)) {
                boolean sweptUp = candle.close() > pool.getLevel().doubleValue();

                LiquiditySweepDetectedEvent event = new LiquiditySweepDetectedEvent();
                event.symbol = symbol;
                event.level = pool.getLevel();
                event.side = sweptUp ? "UP" : "DOWN";
                event.density = pool.getDensity();
                event.reclaimed = true;

                log.info("liquiditySweep symbol={} level={} side={} density={}",
                        symbol, pool.getLevel(), event.side, pool.getDensity());
                eventPublisher.publish(event);
            }
        }
    }

    /**
     * A sweep is a wick beyond the pool with the close back on the other side of it:
     * either wicked below and closed back above (sweep of support/EQL), or wicked above
     * and closed back below (sweep of resistance/EQH).
     */
    private boolean isSweep(Candle candle, LiquidityPool pool) {
        double level = pool.getLevel().doubleValue();
        boolean sweptBelowReclaimedAbove = candle.low() < level && candle.close() > level;
        boolean sweptAboveReclaimedBelow = candle.high() > level && candle.close() < level;
        return sweptBelowReclaimedAbove || sweptAboveReclaimedBelow;
    }
}
