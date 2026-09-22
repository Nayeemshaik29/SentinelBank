package com.sentinelbank.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByAccountNumber(String accountNumber);

	List<Account> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

	boolean existsByAccountNumber(String accountNumber);
}
