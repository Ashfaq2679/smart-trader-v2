package com.smarttrader.v2.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.smarttrader.v2.model.UserCredentials;

/**
 * Spring Data MongoDB repository for {@link UserCredentials}.
 */
@Repository
public interface UserCredentialsRepository extends MongoRepository<UserCredentials, String> {
	Optional<UserCredentials> findByUserId(String userId);
	boolean existsByUserId(String userId);
	void deleteByUserId(String userId);
}
