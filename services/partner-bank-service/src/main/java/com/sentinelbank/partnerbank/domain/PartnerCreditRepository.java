package com.sentinelbank.partnerbank.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerCreditRepository extends JpaRepository<PartnerCredit, UUID> {

	Optional<PartnerCredit> findByTransferId(UUID transferId);

	boolean existsByTransferId(UUID transferId);
}
