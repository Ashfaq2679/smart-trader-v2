package com.smarttrader.v2.feedback;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.StrategyRiskBudget;

/**
 * Persistence for StrategyRiskBudget, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5.3.
 */
@Repository
public interface StrategyRiskBudgetRepository extends MongoRepository<StrategyRiskBudget, String> {

    StrategyRiskBudget findByStrategyNameAndSymbol(String strategyName, String symbol);
}
