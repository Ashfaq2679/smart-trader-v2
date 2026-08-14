package com.smarttrader.v2.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.ProductDocument;

@Repository
public interface ProductRepository extends MongoRepository<ProductDocument, String>{

}
