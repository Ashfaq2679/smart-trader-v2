package com.smarttrader.v2.siren;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.Opportunity;

/**
 * Persistence for Opportunity, per V2_TECH_SPEC_v2.5.md section 7 / Phase 3.4.
 */
@Repository
public interface OpportunityRepository extends MongoRepository<Opportunity, String> {

    @Query("{ 'scored': false }")
    List<Opportunity> findUnscoredOpportunities();

    /**
     * "Siren category" isn't a distinct field in this schema; playbook (the originating
     * strategy's class name) is the closest analytics grouping key this codebase has, so
     * this queries on that.
     */
    List<Opportunity> findByPlaybook(String playbook);
}
