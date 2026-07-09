package com.smarttrader.v2.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.smarttrader.v2.model.User;
import com.smarttrader.v2.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks each trading account's available USD funds, used by OrderService to size and
 * validate orders before submission.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	public User findByUserName(String userName) {
		User user = userRepository.findByUserName(userName);
		if (user == null) {
			throw new IllegalArgumentException("no such user: " + userName);
		}
		return user;
	}

	/**
	 * Sets the user's available funds to newFunds (not a delta).
	 */
	public User updateUserFunds(String userName, double newFunds) {
		User user = findByUserName(userName);
		user.setCurrentFunds(newFunds);
		user.setUpdatedAt(Instant.now());
		User saved = userRepository.save(user);
		log.info("user funds updated userName={} currentFunds={}", userName, newFunds);
		return saved;
	}
}
