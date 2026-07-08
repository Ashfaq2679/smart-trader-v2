package com.smarttrader.v2.risk;

import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.portfolio.PortfolioRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Decision flow step 6, "Global Risk Check" (V2_TECH_SPEC_v1.1.md section 5), which sits
 * between per-trade risk filtering (step 5) and returning the final signal (step 7).
 *
 * Delegates to PortfolioRiskService, which implements section 8 "Portfolio Risk Controls"
 * (dynamic correlation matrix, exposure auto-adjustment, adaptive position sizing).
 */
@Component
@RequiredArgsConstructor
public class GlobalRiskCheck {

    private final PortfolioRiskService portfolioRiskService;

    public TradeDecision apply(TradeDecision decision, String productId, double capital, String correlationId, Instant now) {
        return portfolioRiskService.apply(decision, productId, capital, correlationId, now);
    }
}
