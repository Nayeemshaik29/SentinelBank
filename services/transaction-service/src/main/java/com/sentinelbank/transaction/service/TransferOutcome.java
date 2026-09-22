package com.sentinelbank.transaction.service;

import com.sentinelbank.transaction.domain.Transfer;

/** Whether {@link TransferService#create} made a new transfer or handed back an existing one (a replay). */
public record TransferOutcome(Transfer transfer, boolean created) {
}
