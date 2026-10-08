package com.bracits.transactionservice.api.mapper;

import com.bracits.transactionservice.api.dto.response.ReconciliationReport;
import com.bracits.transactionservice.application.result.ReconciliationResult;

/**
 * Reconciliation result → report DTO.
 */
public interface ReconciliationApiMapper {

  ReconciliationReport toReport(ReconciliationResult result);
}
