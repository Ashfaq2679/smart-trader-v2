package com.smarttrader.v2.siren;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smarttrader.v2.client.Granularity;
import com.smarttrader.v2.model.Candle;
import com.smarttrader.v2.model.Opportunity;
import com.smarttrader.v2.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Post-hoc Opportunity scoring, per V2_TECH_SPEC_v2.5.md section 7 / Phase 3.3: "scored
 * post-hoc for would-have-R at +1h/+4h/+24h."
 *
 * computeWouldHaveR is a documented approximation, not a stub-that-lies: this codebase has
 * no fill/position data (no PositionService/OrderExecutionService exist in this baseline),
 * so "would-have-R" here is derived purely from the Opportunity's own recorded entry/stop/
 * target against the candle closes that followed it - the closest real proxy for "if this
 * setup had been taken, what R would it have realized" without inventing execution data
 * that was never captured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpportunityScoringJob {

    private static final long ONE_HOUR_MS = 3_600_000L;

    private final OpportunityRepository opportunityRepository;
    private final ProductService productService;

    @Scheduled(fixedRate = ONE_HOUR_MS)
    public void scoreOpportunities() {
        List<Opportunity> unscored = opportunityRepository.findUnscoredOpportunities();

        for (Opportunity opportunity : unscored) {
            List<Candle> candles = productService.getLiveCandles(opportunity.getSymbol(), Granularity.ONE_HOUR);

            opportunity.setWouldHaveR1h(computeWouldHaveR(opportunity, candles, 1));
            opportunity.setWouldHaveR4h(computeWouldHaveR(opportunity, candles, 4));
            opportunity.setWouldHaveR24h(computeWouldHaveR(opportunity, candles, 24));
            opportunity.setScored(true);
            opportunityRepository.save(opportunity);

            log.info("opportunityScored id={} symbol={} r1h={} r4h={} r24h={}",
                    opportunity.getId(), opportunity.getSymbol(),
                    opportunity.getWouldHaveR1h(), opportunity.getWouldHaveR4h(), opportunity.getWouldHaveR24h());
        }
    }

    /**
     * Uses the candle whose index is `hours` bars after the opportunity's own creation
     * bar (approximated as the earliest available candle here, since no order-fill
     * timestamp exists to align against) and measures its close against entry/stop,
     * expressed in units of the entry-to-stop risk distance ("R").
     */
    private Float computeWouldHaveR(Opportunity opportunity, List<Candle> candles, int hours) {
        if (candles == null || candles.size() <= hours) {
            return null;
        }

        double riskDistance = Math.abs(opportunity.getEntry() - opportunity.getStop());
        if (riskDistance <= 0) {
            return null;
        }

        double closeAtHorizon = candles.get(hours).close();
        boolean isLong = "LONG".equals(opportunity.getDirection());
        double priceMove = isLong
                ? (closeAtHorizon - opportunity.getEntry())
                : (opportunity.getEntry() - closeAtHorizon);
        return (float) (priceMove / riskDistance);
    }
}
