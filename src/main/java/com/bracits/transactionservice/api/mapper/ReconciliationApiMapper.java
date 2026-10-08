package com.bracits.transactionservice.api.mapper;

import com.bracits.transactionservice.api.dto.ReconciliationMismatch;
import com.bracits.transactionservice.api.dto.ReconciliationReport;
import com.bracits.transactionservice.application.result.ReconciliationResult;
import org.springframework.stereotype.Component;

/** Reconciliation result → report DTO. */
@Component
public final class ReconciliationApiMapper {

  public ReconciliationReport toReport(ReconciliationResult result) {
    return new ReconciliationReport(
        result.from(),
        result.to(),
        result.checked(),
        result.truncated(),
        result.mismatches().stream().map(this::toMismatch).toList());
  }

  private ReconciliationMismatch toMismatch(ReconciliationResult.Mismatch mismatch) {
    return new ReconciliationMismatch(
        mismatch.txnId(),
        mismatch.recordStatus().name(),
        mismatch.ledgerStatus().name(),
        mismatch.action().name());
  }
}
