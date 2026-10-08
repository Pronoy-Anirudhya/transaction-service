package com.bracits.transactionservice.api.controller;

import com.bracits.transactionservice.api.constant.ApiConstants;
import com.bracits.transactionservice.api.constant.ApiMessages;
import com.bracits.transactionservice.api.dto.response.ReconciliationReport;
import com.bracits.transactionservice.api.enums.ApiErrorCode;
import com.bracits.transactionservice.api.exception.ApiException;
import com.bracits.transactionservice.api.mapper.ReconciliationApiMapper;
import com.bracits.transactionservice.application.reconciliation.service.ReconciliationService;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-08: report mismatches between transaction records and ledger postings for {@code [from, to)}.
 */
@RestController
public class ReconciliationController {

  private final ReconciliationService reconciliation;
  private final ReconciliationApiMapper mapper;

  public ReconciliationController(ReconciliationService reconciliation,
      ReconciliationApiMapper mapper) {
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
