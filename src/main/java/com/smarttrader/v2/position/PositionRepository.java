package com.smarttrader.v2.position;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for {@link PositionDocument}.
 */
public interface PositionRepository extends MongoRepository<PositionDocument, String> {
}
