package com.smarttrader.v2.validation;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.TradeOutcome;

/**
 * Persistence for TradeOutcome, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.4.
 */
@Repository
public interface TradeOutcomeRepository extends MongoRepository<TradeOutcome, String> {

    List<TradeOutcome> findTop20ByStrategyNameAndSymbolOrderByClosedAtDesc(String strategyName, String symbol);
}
