package com.sentinelbank.notification.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

	Optional<NotificationLog> findByTransferId(UUID transferId);

	boolean existsByTransferId(UUID transferId);
}
