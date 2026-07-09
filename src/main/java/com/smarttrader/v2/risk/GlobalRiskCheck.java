package com.smarttrader.v2.risk;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.smarttrader.v2.model.TradeDecision;
import com.smarttrader.v2.portfolio.PortfolioRiskService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Decision flow step 6, "Global Risk Check" (V2_TECH_SPEC_v1.1.md section 5), which sits
 * between per-trade risk filtering (step 5) and returning the final signal (step 7).
 *
 * Delegates to PortfolioRiskService, which implements section 8 "Portfolio Risk Controls"
 * (dynamic correlation matrix, exposure auto-adjustment, adaptive position sizing).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GlobalRiskCheck {

    private final PortfolioRiskService portfolioRiskService;
    
    @PostConstruct
    public void init() {
		log.info("GlobalRiskCheck initialized with PortfolioRiskService: {}", portfolioRiskService.getClass().getSimpleName());
	}

    public TradeDecision apply(TradeDecision decision, String productId, double capital, String correlationId, Instant now) {
        return portfolioRiskService.apply(decision, productId, capital, correlationId, now);
    }
}
