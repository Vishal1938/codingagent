package com.dev.codingagent.repository;


import com.dev.codingagent.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    // String instead of Long — MongoDB ObjectId is a String
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}