package com.smarttrader.v2.strategy;

import com.smarttrader.v2.model.TradeDirection;

/**
 * Shared R:R math so each strategy doesn't reimplement it (no duplicated calculations).
 */
final class RiskRewardCalculator {

    private RiskRewardCalculator() {
    }

    /**
     * Risk:reward for a trade. Returns 0 if risk is non-positive (invalid stop placement).
     */
    static double riskReward(TradeDirection direction, double entry, double stop, double target) {
        double risk = direction == TradeDirection.LONG ? entry - stop : stop - entry;
        double reward = direction == TradeDirection.LONG ? target - entry : entry - target;
        if (risk <= 0 || reward <= 0) {
            return 0;
        }
        return reward / risk;
    }
}
