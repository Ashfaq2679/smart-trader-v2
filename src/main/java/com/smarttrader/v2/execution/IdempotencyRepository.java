package com.smarttrader.v2.execution;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for {@link IdempotencyRecordDocument}.
 */
public interface IdempotencyRepository extends MongoRepository<IdempotencyRecordDocument, String> {
}
