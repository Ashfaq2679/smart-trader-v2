package com.smarttrader.v2.feedback;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.ConfigChangeRecord;

/**
 * Persistence for ConfigChangeRecord, per V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md Phase 5.4.
 */
@Repository
public interface ConfigChangeRepository extends MongoRepository<ConfigChangeRecord, String> {
}
