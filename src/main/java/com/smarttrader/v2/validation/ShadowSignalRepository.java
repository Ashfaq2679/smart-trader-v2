package com.smarttrader.v2.validation;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.ShadowSignal;

/**
 * Persistence for ShadowSignal, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 4.5.
 */
@Repository
public interface ShadowSignalRepository extends MongoRepository<ShadowSignal, String> {

    List<ShadowSignal> findByStrategyNameAndSymbolAndDetectedAtAfterOrderByDetectedAtAsc(
            String strategyName, String symbol, Instant since);
}
