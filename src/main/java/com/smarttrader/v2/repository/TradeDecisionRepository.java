package com.smarttrader.v2.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.smarttrader.v2.model.TradeDecisionDocument;

public interface TradeDecisionRepository extends MongoRepository<TradeDecisionDocument, String> {
    List<TradeDecisionDocument> findByProductIdOrderByTimestampDesc(String productId);
}
