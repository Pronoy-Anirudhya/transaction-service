package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.config.MetricConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/** Business counters and timers of spec 12 ({@code sendmoney_requests_total{outcome,code}} and friends). */
@Component
public final class SendMoneyMetrics {

  private final MeterRegistry registry;
  private final Counter limitRejections;
  private final Counter reconciliationMismatches;

  public SendMoneyMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.limitRejections = registry.counter(MetricConstants.LIMIT_REJECTIONS);
    this.reconciliationMismatches = registry.counter(MetricConstants.RECONCILIATION_MISMATCHES);
  }

  public void recordSend(SendMoneyResult result, Duration duration) {
    String outcome = outcomeOf(result);
    String code = codeOf(result);
    registry.counter(MetricConstants.SENDMONEY_REQUESTS,
        MetricConstants.TAG_OUTCOME, outcome, MetricConstants.TAG_CODE, code).increment();
    Timer.builder(MetricConstants.SENDMONEY_DURATION)
        .tag(MetricConstants.TAG_OUTCOME, outcome)
        .publishPercentileHistogram()
        .register(registry)
        .record(duration);
  }

  public void limitRejected() {
    limitRejections.increment();
  }

  public void repairAttempt(String outcome) {
    registry.counter(MetricConstants.TXN_REPAIR_ATTEMPTS, MetricConstants.TAG_OUTCOME, outcome).increment();
  }

  public void reconciliationMismatch() {
    reconciliationMismatches.increment();
  }

  public static String outcomeOf(SendMoneyResult result) {
    return switch (result) {
      case SendMoneyResult.Completed c -> MetricOutcome.COMPLETED.tag();
      case SendMoneyResult.Processing p -> MetricOutcome.PROCESSING.tag();
      case SendMoneyResult.Rejected r -> MetricOutcome.REJECTED.tag();
      case SendMoneyResult.InvalidQuoteToken i -> MetricOutcome.INVALID.tag();
    };
  }

  public static String codeOf(SendMoneyResult result) {
    return switch (result) {
      case SendMoneyResult.Rejected r -> r.code().name();
      case SendMoneyResult.Completed c -> MetricConstants.NONE;
      case SendMoneyResult.Processing p -> MetricConstants.NONE;
      case SendMoneyResult.InvalidQuoteToken i -> MetricConstants.NONE;
    };
  }

  /** Values of the {@code outcome} tag. */
  public enum MetricOutcome {
    COMPLETED, PROCESSING, REJECTED, INVALID, FAILED, IN_DOUBT;

    public String tag() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
