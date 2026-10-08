package com.bracits.transactionservice.application.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bracits.transactionservice.application.enums.ReconciliationAction;
import com.bracits.transactionservice.application.enums.ReconciliationLedgerView;
import com.bracits.transactionservice.application.fakes.FakeLedgerHealth;
import com.bracits.transactionservice.application.mapper.impl.SendMoneyEventMapperImpl;
import com.bracits.transactionservice.application.metrics.service.impl.SendMoneyMetricsImpl;
import com.bracits.transactionservice.application.posting.service.impl.TxnFinaliserImpl;
import com.bracits.transactionservice.application.reconciliation.service.impl.ReconciliationServiceImpl;
import com.bracits.transactionservice.application.result.ReconciliationResult.Mismatch;
import com.bracits.transactionservice.application.result.ReconciliationResult;
import com.bracits.transactionservice.config.properties.ReconciliationProperties;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import com.bracits.transactionservice.domain.ledger.enums.PostingLookupStatus;
import com.bracits.transactionservice.domain.ledger.model.PostingLookup;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.port.out.client.LedgerQueryPort;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import com.bracits.transactionservice.port.out.publisher.EventPublisherPort;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Reconciliation edge cases: lost "ledger wins" race, unexpected look-up errors, alert metric
 * hygiene.
 */
class ReconciliationEdgeCasesTest {

  private static final Instant FROM = Instant.parse("2026-10-07T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-10-08T00:00:00Z");

  private final TxnRepository txns = mock(TxnRepository.class);
  private final LedgerQueryPort ledger = mock(LedgerQueryPort.class);
  private final EventPublisherPort events = mock(EventPublisherPort.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ReconciliationService service = new ReconciliationServiceImpl(
      txns,
      ledger,
      new TxnFinaliserImpl(txns, events, new SendMoneyEventMapperImpl()),
      new SendMoneyMetricsImpl(registry),
      new ReconciliationProperties(100, 4),
      new FakeLedgerHealth());

  @Test
  void lostLedgerWinsRaceReportsNothingAndPublishesNothing() {
    SendMoneyTxn failed = failedRow();
    given(txns.findByTxnIdRange(any(), any(), anyInt())).willReturn(List.of(failed));
    given(ledger.lookupPosting(any(), anyInt()))
        .willReturn(new PostingLookup(PostingLookupStatus.POSTED, OptionalLong.of(7L)));
    given(txns.markFailedAsCompleted(failed.txnId(), OptionalLong.of(7L))).willReturn(
        Optional.empty());

    ReconciliationResult result = service.reconcile(FROM, TO);

    assertThat(result.mismatches()).isEmpty();
    verify(events, never()).publish(any());
    assertThat(registry.counter("reconciliation.mismatches").count()).isZero();
  }

  @Test
  void unexpectedLookupErrorMarksOnlyThatRowNotChecked() {
    SendMoneyTxn failed = failedRow();
    given(txns.findByTxnIdRange(any(), any(), anyInt())).willReturn(List.of(failed));
    given(ledger.lookupPosting(any(), anyInt())).willThrow(
        new IllegalStateException("garbled answer"));

    ReconciliationResult result = service.reconcile(FROM, TO);

    assertThat(result.mismatches()).containsExactly(
        new Mismatch(failed.txnId(), TxnStatus.FAILED, ReconciliationLedgerView.UNKNOWN,
            ReconciliationAction.NOT_CHECKED));
  }

  @Test
  void notCheckedDoesNotTripTheMismatchAlertMetric() {
    given(txns.findByTxnIdRange(any(), any(), anyInt())).willReturn(List.of(failedRow()));
    given(ledger.lookupPosting(any(), anyInt())).willThrow(new LedgerUnavailableException("503"));

    service.reconcile(FROM, TO);

    assertThat(registry.counter("reconciliation.mismatches").count()).isZero();
  }

  private static SendMoneyTxn failedRow() {
    return new SendMoneyTxn(
        UUID.fromString("01a11a3f-e38b-d8a7-ad7c-e0f6afe58e00"),
        "key-1",
        new RequestHash(new byte[32]),
        1L,
        2L,
        100_000L,
        new Pricing(500, 65, 87, 348),
        "BDT",
        Optional.empty(),
        LocalDate.parse("2026-10-07"),
        TxnStatus.FAILED,
        Optional.of(FailureCode.INSUFFICIENT_FUNDS),
        1,
        Optional.empty(),
        OptionalLong.empty(),
        FROM.plusSeconds(60),
        Optional.of(FROM.plusSeconds(61)),
        Optional.empty());
  }
}
