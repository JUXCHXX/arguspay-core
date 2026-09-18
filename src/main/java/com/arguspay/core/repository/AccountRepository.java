package com.arguspay.core.repository;

import com.arguspay.core.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
	List<Account> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
