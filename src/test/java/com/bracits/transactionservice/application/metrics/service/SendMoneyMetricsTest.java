package com.bracits.transactionservice.application.metrics.service;

import static com.bracits.transactionservice.application.fakes.Fixtures.AMOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.STANDARD_PRICING;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.application.enums.MetricOutcome;
import com.bracits.transactionservice.application.metrics.service.impl.SendMoneyMetricsImpl;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.result.TxnSummary;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SendMoneyMetricsTest {

  private static final UUID TXN_ID = UUID.fromString("0192f5a4-1111-2222-3333-444444444400");
  private static final TxnSummary SUMMARY =
      new TxnSummary(TXN_ID, TxnStatus.COMPLETED, AMOUNT, STANDARD_PRICING, Optional.empty(),
          Optional.empty());

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final SendMoneyMetrics metrics = new SendMoneyMetricsImpl(registry);

  @Test
  void outcomeAndCodeTags() {
    SendMoneyResult completed = new SendMoneyResult.Completed(SUMMARY);
    SendMoneyResult processing = new SendMoneyResult.Processing(SUMMARY);
    SendMoneyResult rejected = new SendMoneyResult.Rejected(FailureCode.LIMIT_EXCEEDED,
        Optional.empty());
    SendMoneyResult invalid = new SendMoneyResult.InvalidQuoteToken();

    assertThat(SendMoneyMetrics.outcomeOf(completed)).isEqualTo("completed");
    assertThat(SendMoneyMetrics.outcomeOf(processing)).isEqualTo("processing");
    assertThat(SendMoneyMetrics.outcomeOf(rejected)).isEqualTo("rejected");
    assertThat(SendMoneyMetrics.outcomeOf(invalid)).isEqualTo("invalid");

    assertThat(SendMoneyMetrics.codeOf(completed)).isEqualTo("none");
    assertThat(SendMoneyMetrics.codeOf(processing)).isEqualTo("none");
    assertThat(SendMoneyMetrics.codeOf(rejected)).isEqualTo("LIMIT_EXCEEDED");
    assertThat(SendMoneyMetrics.codeOf(invalid)).isEqualTo("none");
  }

  @Test
  void outcomeTagsAreLowerCase() {
    assertThat(MetricOutcome.IN_DOUBT.tag()).isEqualTo("in_doubt");
    assertThat(MetricOutcome.FAILED.tag()).isEqualTo("failed");
  }

  @Test
  void recordSendCountsByOutcomeAndCodeAndTimesByOutcome() {
    SendMoneyResult rejected = new SendMoneyResult.Rejected(FailureCode.INSUFFICIENT_FUNDS,
        Optional.of(TXN_ID));

    metrics.recordSend(rejected, Duration.ofMillis(40));
    metrics.recordSend(rejected, Duration.ofMillis(60));
    metrics.recordSend(new SendMoneyResult.Completed(SUMMARY), Duration.ofMillis(10));

    assertThat(
        registry.get("sendmoney.requests").tags("outcome", "rejected", "code", "INSUFFICIENT_FUNDS")
            .counter().count()).isEqualTo(2.0);
    assertThat(registry.get("sendmoney.requests").tags("outcome", "completed", "code", "none")
        .counter().count()).isEqualTo(1.0);

    Timer timer = registry.get("sendmoney.duration").tag("outcome", "rejected").timer();
    assertThat(timer.count()).isEqualTo(2L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100.0);
  }

  @Test
  void businessCounters() {
    metrics.limitRejected();
    metrics.limitRejected();
    metrics.repairAttempt("completed");
    metrics.repairAttempt(MetricOutcome.IN_DOUBT.tag());
    metrics.repairAttempt(MetricOutcome.IN_DOUBT.tag());
    metrics.reconciliationMismatch();

    assertThat(registry.get("limit.rejections").counter().count()).isEqualTo(2.0);
    assertThat(registry.get("txn.repair.attempts").tag("outcome", "completed").counter()
        .count()).isEqualTo(1.0);
    assertThat(
        registry.get("txn.repair.attempts").tag("outcome", "in_doubt").counter().count()).isEqualTo(
        2.0);
    assertThat(registry.get("reconciliation.mismatches").counter().count()).isEqualTo(1.0);
  }

  @Test
  void countersExistFromStartUp() {
    assertThat(registry.get("limit.rejections").counter().count()).isZero();
    assertThat(registry.get("reconciliation.mismatches").counter().count()).isZero();
  }
}
