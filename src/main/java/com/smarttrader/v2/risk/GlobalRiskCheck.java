package com.smarttrader.v2.risk;

import com.smarttrader.v2.model.TradeDecision;
import org.springframework.stereotype.Component;

/**
 * Decision flow step 6, "Global Risk Check" (V2_TECH_SPEC_v1.1.md section 5), which sits
 * between per-trade risk filtering (step 5) and returning the final signal (step 7).
 *
 * This is intentionally a pass-through placeholder: the actual portfolio-level controls
 * (dynamic correlation matrix, exposure auto-adjustment, adaptive position sizing) are
 * defined in section 8 "Portfolio Risk Controls" and are explicitly not implemented yet.
 * Wiring this step in now keeps TradeEngine's flow structurally aligned with the spec so
 * section 8 can slot in here without changing the pipeline shape.
 */
@Component
public class GlobalRiskCheck {

    public TradeDecision apply(TradeDecision decision) {
        return decision;
    }
}
