package com.smarttrader.v2.liquidity;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.LiquidityPool;

/**
 * Spring Data MongoDB repository for LiquidityPool documents, per
 * V2_5_IMPLEMENTATION_PLAN_INCREMENTAL.md section 1A.4. "Active" here means not yet
 * TTL-expired (see LiquidityPool.expiresAt) - expired documents are removed by MongoDB's
 * TTL monitor, so filtering by symbol alone already reflects only active pools in
 * practice (an explicit @Query is used since "ActivePools" isn't itself a document
 * property Spring Data could derive a query from).
 */
@Repository
public interface LiquidityPoolRepository extends MongoRepository<LiquidityPool, String> {

    @Query("{ 'symbol': ?0 }")
    List<LiquidityPool> findActivePoolsBySymbol(String symbol);
}
