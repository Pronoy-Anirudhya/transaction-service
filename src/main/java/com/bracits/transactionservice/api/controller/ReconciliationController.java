package com.bracits.transactionservice.api.controller;

import com.bracits.transactionservice.api.ApiConstants;
import com.bracits.transactionservice.api.dto.ReconciliationReport;
import com.bracits.transactionservice.api.error.ApiErrorCode;
import com.bracits.transactionservice.api.error.ApiException;
import com.bracits.transactionservice.api.error.ApiMessages;
import com.bracits.transactionservice.api.mapper.ReconciliationApiMapper;
import com.bracits.transactionservice.application.ReconciliationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** FR-08: report mismatches between transaction records and ledger postings for {@code [from, to)}. */
@RestController
public class ReconciliationController {

  private final ReconciliationService reconciliation;
  private final ReconciliationApiMapper mapper;

  public ReconciliationController(ReconciliationService reconciliation, ReconciliationApiMapper mapper) {
    this.reconciliation = reconciliation;
    this.mapper = mapper;
  }

  @GetMapping(ApiConstants.RECONCILIATION_PATH)
  public ReconciliationReport reconcile(
      @RequestParam(ApiConstants.PARAM_FROM) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(ApiConstants.PARAM_TO) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    if (!from.isBefore(to)) {
      throw new ApiException(ApiErrorCode.VALIDATION_FAILED, ApiMessages.INVALID_WINDOW);
    }
    return mapper.toReport(reconciliation.reconcile(from, to));
  }
}
