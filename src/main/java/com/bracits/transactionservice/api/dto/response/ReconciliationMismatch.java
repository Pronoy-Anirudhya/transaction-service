package com.bracits.transactionservice.api.dto.response;

import java.util.UUID;

/**
 * One record/ledger disagreement. {@code recordStatus} is the row status before any action;
 * {@code ledgerStatus} is POSTED / NOT_FOUND / UNKNOWN; {@code action} says what reconciliation did
 * (e.g. FLIPPED_TO_COMPLETED, ALERT_ONLY).
 */
public record ReconciliationMismatch(UUID txnId, String recordStatus, String ledgerStatus,
                                     String action) {

}
