package com.arguspay.core.repository;

import com.arguspay.core.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
