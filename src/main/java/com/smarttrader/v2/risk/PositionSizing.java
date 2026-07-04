package com.smarttrader.v2.risk;

import org.springframework.stereotype.Component;

/**
 * Position sizing, per V2_TECH_SPEC.md section 4 / SmartTrader_Skills_Spec.md calculatePositionSize:
 *
 *   riskAmount = capital * riskPercent
 *   positionSize = riskAmount / stopDistance
 */
@Component
public class PositionSizing {

    public double calculate(double capital, double riskPercent, double entry, double stop) {
        double stopDistance = Math.abs(entry - stop);
        if (stopDistance <= 0) {
            return 0;
        }
        double riskAmount = capital * riskPercent;
        return riskAmount / stopDistance;
    }
}
