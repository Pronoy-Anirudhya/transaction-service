package com.bracits.transactionservice.application.metrics.service;

import com.bracits.transactionservice.application.enums.MetricOutcome;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.config.constant.MetricConstants;
import java.time.Duration;

/**
 * Business counters and timers of spec 12 ({@code sendmoney_requests_total{outcome,code}} and
 * friends).
 */
public interface SendMoneyMetrics {

  void recordSend(SendMoneyResult result, Duration duration);

  void limitRejected();

  void repairAttempt(String outcome);

  void reconciliationMismatch();

  static String outcomeOf(SendMoneyResult result) {
    return switch (result) {
      case SendMoneyResult.Completed c -> MetricOutcome.COMPLETED.tag();
      case SendMoneyResult.Processing p -> MetricOutcome.PROCESSING.tag();
      case SendMoneyResult.Rejected r -> MetricOutcome.REJECTED.tag();
      case SendMoneyResult.InvalidQuoteToken i -> MetricOutcome.INVALID.tag();
      case SendMoneyResult.LedgerUnavailable u -> MetricOutcome.UNAVAILABLE.tag();
    };
  }

  static String codeOf(SendMoneyResult result) {
    return switch (result) {
      case SendMoneyResult.Rejected r -> r.code().name();
      case SendMoneyResult.Completed c -> MetricConstants.NONE;
      case SendMoneyResult.Processing p -> MetricConstants.NONE;
      case SendMoneyResult.InvalidQuoteToken i -> MetricConstants.NONE;
      case SendMoneyResult.LedgerUnavailable u -> MetricConstants.CODE_LEDGER_UNAVAILABLE;
    };
  }
}
