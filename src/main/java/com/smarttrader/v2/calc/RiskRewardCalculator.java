package com.smarttrader.v2.calc;

import com.smarttrader.v2.model.TradeDirection;

/**
 * Shared risk:reward math, used by both strategy evaluation and RiskEngine filtering,
 * so the calculation is defined once (no duplicated calculations).
 */
public final class RiskRewardCalculator {

    private RiskRewardCalculator() {
    }

    /**
     * Raw risk:reward for a trade. Returns 0 if risk or reward is non-positive
     * (invalid stop/target placement).
     */
    public static double riskReward(TradeDirection direction, double entry, double stop, double target) {
        double risk = risk(direction, entry, stop);
        double reward = reward(direction, entry, target);
        if (risk <= 0 || reward <= 0) {
            return 0;
        }
        return reward / risk;
    }

    /**
     * Fee/slippage-adjusted risk:reward, per V2_TECH_SPEC_v1.1.md section 4:
     *
     *   effectiveReward = target - entry - fees - slippage
     *   effectiveRisk   = entry - stop + fees + slippage
     *
     * (mirrored for SHORT via the direction-adjusted risk/reward legs below).
     * Returns 0 if the effective risk or reward is non-positive.
     */
    public static double effectiveRiskReward(TradeDirection direction, double entry, double stop, double target,
                                              double fees, double slippage) {
        double cost = fees + slippage;
        double effectiveReward = reward(direction, entry, target) - cost;
        double effectiveRisk = risk(direction, entry, stop) + cost;
        if (effectiveRisk <= 0 || effectiveReward <= 0) {
            return 0;
        }
        return effectiveReward / effectiveRisk;
    }

    private static double risk(TradeDirection direction, double entry, double stop) {
        return direction == TradeDirection.LONG ? entry - stop : stop - entry;
    }

    private static double reward(TradeDirection direction, double entry, double target) {
        return direction == TradeDirection.LONG ? target - entry : entry - target;
    }
}
