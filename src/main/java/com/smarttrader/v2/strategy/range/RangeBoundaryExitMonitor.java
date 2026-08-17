package com.smarttrader.v2.strategy.range;

import org.springframework.stereotype.Component;

import com.smarttrader.v2.model.Position;
import com.smarttrader.v2.model.TradeDirection;
import com.smarttrader.v2.strategy.RangeSupportResistanceStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * Mandatory boundary-break exit for {@link RangeSupportResistanceStrategy}, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL_WITH_RANGE_SR.md §2.3.1 lines 873-917.
 *
 * <p>Priority ordering (spec lines 907-914):
 * <ol>
 *   <li>Emergency boundary break: price beyond boundary by {@code emergencyBreakBufferAtr * ATR}.</li>
 *   <li>Confirmed candle close beyond boundary by {@code breakoutBufferAtr * ATR}.</li>
 *   <li>Protective stop (delegated to existing engine).</li>
 *   <li>Normal target (delegated to existing engine).</li>
 * </ol>
 *
 * <p><b>Attribution guard:</b> {@link #shouldExit(Position, RangeStructure, double, double, double)}
 * short-circuits to {@code false} for any position whose {@code openOrderId}/context does not
 * carry the {@code "RangeSupportResistance"} tag - the strategy identifier is checked via
 * the caller-supplied {@link Position} and the utility {@link #ownsPosition(String)}. Callers
 * that lack a strategy tag on {@link Position} must filter before calling.
 *
 * <p><b>Integration seam:</b> this component is defined but intentionally not wired into
 * {@code TradeEngine} - the spec (line 947) permits "the smallest backward-compatible field
 * if required" but forbids rewriting the engine. The wiring hook (a per-price-tick callback
 * from the existing position monitor into {@link #shouldExit}) is added when the feature flag
 * is enabled.
 */
@Slf4j
@Component
public class RangeBoundaryExitMonitor {

    private final RangeSupportResistanceConfig config;
    private final RangeSupportResistanceStrategy strategy;

    public RangeBoundaryExitMonitor(RangeSupportResistanceConfig config,
                                    RangeSupportResistanceStrategy strategy) {
        this.config = config;
        this.strategy = strategy;
    }

    /** True iff the strategy identifier belongs to this monitor (attribution guard). */
    public boolean ownsPosition(String strategyName) {
        return RangeSupportResistanceStrategy.NAME.equals(strategyName);
    }

    /**
     * Evaluate whether {@code position} must be closed now.
     *
     * @param position       the open position (attribution string comes from {@link Position#getOpenOrderId()}
     *                       today; a dedicated {@code strategyName} column is out-of-scope per §2.3.1 rule 4).
     * @param range          the active range structure this position was opened inside.
     * @param strategyName   attribution string carried on the order/position; must equal
     *                       {@link RangeSupportResistanceStrategy#NAME} for this monitor to act.
     * @param price          current mark/last price.
     * @param confirmedClose confirmed candle close (equal to {@code price} if no fresh close).
     * @param atr            current ATR.
     */
    public boolean shouldExit(Position position, RangeStructure range, String strategyName,
                              double price, double confirmedClose, double atr) {
        if (position == null || range == null || !range.valid()) {
            return false;
        }
        if (!ownsPosition(strategyName)) {
            return false;
        }
        double emergencyBuf = config.emergencyBreakBufferAtr() * atr;
        double breakoutBuf = config.breakoutBufferAtr() * atr;

        TradeDirection direction = TradeDirection.LONG.name().equalsIgnoreCase(position.getSide())
                ? TradeDirection.LONG : TradeDirection.SHORT;

        if (direction == TradeDirection.LONG) {
            if (price <= range.support().lower() - emergencyBuf) {
                log.info("strategy={} action=EMERGENCY_EXIT direction=LONG price={} supportLower={}",
                        RangeSupportResistanceStrategy.NAME, price, range.support().lower());
                onBoundaryBreak();
                return true;
            }
            if (confirmedClose < range.support().lower() - breakoutBuf) {
                log.info("strategy={} action=CONFIRMED_EXIT direction=LONG close={} supportLower={}",
                        RangeSupportResistanceStrategy.NAME, confirmedClose, range.support().lower());
                onBoundaryBreak();
                return true;
            }
        } else {
            if (price >= range.resistance().upper() + emergencyBuf) {
                log.info("strategy={} action=EMERGENCY_EXIT direction=SHORT price={} resistanceUpper={}",
                        RangeSupportResistanceStrategy.NAME, price, range.resistance().upper());
                onBoundaryBreak();
                return true;
            }
            if (confirmedClose > range.resistance().upper() + breakoutBuf) {
                log.info("strategy={} action=CONFIRMED_EXIT direction=SHORT close={} resistanceUpper={}",
                        RangeSupportResistanceStrategy.NAME, confirmedClose, range.resistance().upper());
                onBoundaryBreak();
                return true;
            }
        }
        return false;
    }

    private void onBoundaryBreak() {
        // Spec lines 919-935: no averaging, no rescue; mark range invalid and block re-entry
        // until a new validated range is detected.
        strategy.markRangeInvalidated();
    }
}
