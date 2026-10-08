package com.bracits.transactionservice.api.mapper.impl;

import com.bracits.transactionservice.api.dto.response.ReconciliationMismatch;
import com.bracits.transactionservice.api.dto.response.ReconciliationReport;
import com.bracits.transactionservice.api.mapper.ReconciliationApiMapper;
import com.bracits.transactionservice.application.result.ReconciliationResult;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link ReconciliationApiMapper}.
 */
@Component
public final class ReconciliationApiMapperImpl implements ReconciliationApiMapper {

  @Override
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
