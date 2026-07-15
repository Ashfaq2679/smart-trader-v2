package com.smarttrader.v2.validation;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.StrategyState;

/**
 * Persistence for StrategyState, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.2.
 */
@Repository
public interface StrategyStateRepository extends MongoRepository<StrategyState, String> {

    StrategyState findByStrategyNameAndSymbol(String strategyName, String symbol);
}
