package com.bracits.transactionservice.application.metrics.service.impl;

import com.bracits.transactionservice.application.metrics.service.SendMoneyMetrics;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.config.constant.MetricConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link SendMoneyMetrics}.
 */
@Component
public final class SendMoneyMetricsImpl implements SendMoneyMetrics {

  private final MeterRegistry registry;
  private final Counter limitRejections;
  private final Counter reconciliationMismatches;

  public SendMoneyMetricsImpl(MeterRegistry registry) {
    this.registry = registry;
    this.limitRejections = registry.counter(MetricConstants.LIMIT_REJECTIONS);
    this.reconciliationMismatches = registry.counter(MetricConstants.RECONCILIATION_MISMATCHES);
  }

  @Override
  public void recordSend(SendMoneyResult result, Duration duration) {
    String outcome = SendMoneyMetrics.outcomeOf(result);
    String code = SendMoneyMetrics.codeOf(result);

    registry.counter(MetricConstants.SENDMONEY_REQUESTS,
        MetricConstants.TAG_OUTCOME, outcome, MetricConstants.TAG_CODE, code).increment();

    Timer.builder(MetricConstants.SENDMONEY_DURATION)
        .tag(MetricConstants.TAG_OUTCOME, outcome)
        .publishPercentileHistogram()
        .register(registry)
        .record(duration);
  }

  @Override
  public void limitRejected() {
    limitRejections.increment();
  }

  @Override
  public void repairAttempt(String outcome) {
    registry.counter(MetricConstants.TXN_REPAIR_ATTEMPTS, MetricConstants.TAG_OUTCOME, outcome)
        .increment();
  }

  @Override
  public void reconciliationMismatch() {
    reconciliationMismatches.increment();
  }

}
