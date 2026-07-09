package com.smarttrader.v2.backtest;

import com.smarttrader.v2.model.EntryType;
import com.smarttrader.v2.model.SignalResult;
import com.smarttrader.v2.model.TradeDirection;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Simulated fills (V2_TECH_SPEC_v1.1.md section 12): deterministically decides whether a
 * signal would have filled at a given simulated price, without touching any real exchange.
 *
 * MARKET orders fill immediately at the current simulated price. LIMIT orders only fill
 * once price has reached (or crossed through) the limit/entry price, matching real
 * exchange behavior for resting limit orders.
 */
@Component
public class SimulatedFillEngine {

    public Optional<Double> simulateFill(SignalResult signal, double currentPrice) {
        if (signal.entryType() == EntryType.MARKET) {
            return Optional.of(currentPrice);
        }

        boolean reached = signal.direction() == TradeDirection.LONG
                ? currentPrice <= signal.entry()
                : currentPrice >= signal.entry();
        return reached ? Optional.of(signal.entry()) : Optional.empty();
    }
}
