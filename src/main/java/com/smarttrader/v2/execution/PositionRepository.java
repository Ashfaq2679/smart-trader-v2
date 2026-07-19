package com.smarttrader.v2.execution;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.Position;
import com.smarttrader.v2.model.PositionStatus;

@Repository
public interface PositionRepository extends MongoRepository<Position, String> {

    Optional<Position> findFirstBySymbolAndStatusOrderByOpenedAtDesc(String symbol, PositionStatus status);

    List<Position> findBySymbolOrderByOpenedAtDesc(String symbol);
}
