package com.bracits.transactionservice.application;

import ch.qos.logback.classic.Level;
import com.bracits.transactionservice.application.fakes.FakeLedgerQueryPort;
import com.bracits.transactionservice.application.fakes.FakeLedgerQueryPort.Lookup;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Flip;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Range;
import com.bracits.transactionservice.application.fakes.LogCapture;
import com.bracits.transactionservice.application.fakes.SendMoneyHarness;
import com.bracits.transactionservice.application.fakes.TxnRowBuilder;
import com.bracits.transactionservice.application.result.ReconciliationResult;
import com.bracits.transactionservice.application.result.ReconciliationResult.Action;
import com.bracits.transactionservice.application.result.ReconciliationResult.LedgerView;
import com.bracits.transactionservice.application.result.ReconciliationResult.Mismatch;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.event.EventType;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.domain.txn.TxnIds;
import com.bracits.transactionservice.port.out.LedgerUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static com.bracits.transactionservice.application.fakes.Fixtures.LEDGER_TS;
import static com.bracits.transactionservice.application.fakes.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ReconciliationServiceTest {

  private static final Instant FROM = NOW.minusSeconds(3_600);
  private static final Instant TO = NOW.plusSeconds(1);

  private final SendMoneyHarness h = new SendMoneyHarness();
  private final FakeLedgerQueryPort ledger = new FakeLedgerQueryPort();
  private final ReconciliationService service = h.reconciliation(ledger, 100);

  private SendMoneyTxn seed(TxnStatus status) {
    UUID txnId = h.ids.next();
    TxnRowBuilder row = TxnRowBuilder.row(txnId).clientRef(txnId.toString()).createdAt(NOW.minusSeconds(60));
    switch (status) {
      case COMPLETED -> row.completed(LEDGER_TS, NOW.minusSeconds(59));
      case FAILED -> row.failed(FailureCode.INSUFFICIENT_FUNDS, NOW.minusSeconds(59));
      case INITIATED -> { }
    }
    SendMoneyTxn built = row.build();
    h.txns.put(built);
    return built;
  }

  @Test
  void completedAndPostedOrFailedAndNotFoundAgree() {
    SendMoneyTxn completed = seed(TxnStatus.COMPLETED);
    seed(TxnStatus.FAILED);
    ledger.posted(completed.txnId(), OptionalLong.of(LEDGER_TS));

    ReconciliationResult result = service.reconcile(FROM, TO);

    assertThat(result.checked()).isEqualTo(2);
    assertThat(result.truncated()).isFalse();
    assertThat(result.mismatches()).isEmpty();
    assertThat(result.from()).isEqualTo(FROM);
    assertThat(result.to()).isEqualTo(TO);
    assertThat(h.txns.flips()).isEmpty();
    assertThat(h.events.events()).isEmpty();
    assertThat(h.counter("reconciliation.mismatches")).isZero();
  }

  @Test
  void failedButPostedIsFlippedToCompletedWithTheLedgerTimestamp() {
    SendMoneyTxn failed = seed(TxnStatus.FAILED);
    ledger.posted(failed.txnId(), OptionalLong.of(LEDGER_TS));

    try (LogCapture log = LogCapture.of(ReconciliationService.class)) {
      ReconciliationResult result = service.reconcile(FROM, TO);

      assertThat(result.mismatches()).containsExactly(
          new Mismatch(failed.txnId(), TxnStatus.FAILED, LedgerView.POSTED, Action.FLIPPED_TO_COMPLETED));
      assertThat(log.templates(Level.ERROR)).containsExactly(ApplicationConstants.ALERT_LEDGER_WINS);
    }
    assertThat(h.txns.flips()).containsExactly(new Flip(failed.txnId(), OptionalLong.of(LEDGER_TS)));
    SendMoneyTxn stored = h.txns.get(failed.txnId());
    assertThat(stored.status()).isEqualTo(TxnStatus.COMPLETED);
    assertThat(stored.failureCode()).isEmpty();
    assertThat(stored.ledgerTimestamp()).hasValue(LEDGER_TS);
    assertThat(h.events.events()).singleElement().satisfies(event -> {
      assertThat(event.eventType()).isEqualTo(EventType.SEND_MONEY_COMPLETED);
      assertThat(event.txnId()).isEqualTo(failed.txnId());
      assertThat(event.failureCode()).isEmpty();
    });
    assertThat(h.events.statusesAtPublish()).containsExactly(Optional.of(TxnStatus.COMPLETED));
    assertThat(h.counter("reconciliation.mismatches")).isEqualTo(1.0);
  }

  @Test
  void failedButPostedWithoutALedgerTimestampFlipsWithAnEmptyTimestamp() {
    SendMoneyTxn failed = seed(TxnStatus.FAILED);
    ledger.posted(failed.txnId(), OptionalLong.empty());

    service.reconcile(FROM, TO);

    assertThat(h.txns.flips()).containsExactly(new Flip(failed.txnId(), OptionalLong.empty()));
    assertThat(h.txns.get(failed.txnId()).ledgerTimestamp()).isEmpty();
  }

  @Test
  void completedButNotPostedRaisesAnAlertAndChangesNothing() {
    SendMoneyTxn completed = seed(TxnStatus.COMPLETED);

    try (LogCapture log = LogCapture.of(ReconciliationService.class)) {
      ReconciliationResult result = service.reconcile(FROM, TO);

      assertThat(result.mismatches()).containsExactly(
          new Mismatch(completed.txnId(), TxnStatus.COMPLETED, LedgerView.NOT_FOUND, Action.ALERT_RAISED));
      assertThat(log.templates(Level.ERROR)).containsExactly(ApplicationConstants.ALERT_COMPLETED_NOT_POSTED);
    }
    assertThat(h.txns.get(completed.txnId())).isEqualTo(completed);
    assertThat(h.txns.flips()).isEmpty();
    assertThat(h.events.events()).isEmpty();
  }

  @Test
  void ledgerUnavailableIsNotChecked() {
    SendMoneyTxn completed = seed(TxnStatus.COMPLETED);
    ledger.failLookup(completed.txnId(), new LedgerUnavailableException("503"));

    ReconciliationResult result = service.reconcile(FROM, TO);

    assertThat(result.mismatches()).containsExactly(
        new Mismatch(completed.txnId(), TxnStatus.COMPLETED, LedgerView.UNKNOWN, Action.NOT_CHECKED));
    assertThat(h.txns.get(completed.txnId())).isEqualTo(completed);
  }

  @Test
  void initiatedRowsAreLeftToTheRepairWorker() {
    SendMoneyTxn initiated = seed(TxnStatus.INITIATED);
    ledger.posted(initiated.txnId(), OptionalLong.of(LEDGER_TS));

    ReconciliationResult result = service.reconcile(FROM, TO);

    assertThat(result.mismatches()).isEmpty();
    assertThat(ledger.lookups()).isEmpty();
    assertThat(h.txns.get(initiated.txnId()).status()).isEqualTo(TxnStatus.INITIATED);
  }

  @Test
  void looksUpWithTheLegCountOfTheStoredPricing() {
    SendMoneyTxn standard = seed(TxnStatus.COMPLETED);
    SendMoneyTxn free = TxnRowBuilder.from(seed(TxnStatus.COMPLETED)).pricing(Pricing.FREE).build();
    h.txns.put(free);
    ledger.posted(standard.txnId(), OptionalLong.of(LEDGER_TS));
    ledger.posted(free.txnId(), OptionalLong.of(LEDGER_TS));

    service.reconcile(FROM, TO);

    assertThat(ledger.lookups()).containsExactlyInAnyOrder(
        new Lookup(standard.txnId(), 4), new Lookup(free.txnId(), 1));
  }

  @Test
  void scansTheWindowByTxnIdBoundsAndAsksForOneRowMoreThanTheCap() {
    service.reconcile(FROM, TO);

    assertThat(h.txns.ranges()).containsExactly(
        new Range(TxnIds.lowerBound(FROM), TxnIds.lowerBound(TO), 101));
  }

  @Test
  void moreRowsThanTheCapAreTruncated() {
    ReconciliationService capped = h.reconciliation(ledger, 2);
    SendMoneyTxn first = seed(TxnStatus.COMPLETED);
    SendMoneyTxn second = seed(TxnStatus.COMPLETED);
    SendMoneyTxn third = seed(TxnStatus.COMPLETED);

    ReconciliationResult result = capped.reconcile(FROM, TO);

    assertThat(result.truncated()).isTrue();
    assertThat(result.checked()).isEqualTo(2);
    assertThat(result.mismatches()).extracting(Mismatch::txnId).containsExactly(first.txnId(), second.txnId());
    assertThat(ledger.lookups()).extracting(Lookup::postingId).doesNotContain(third.txnId());
  }

  @Test
  void exactlyTheCapIsNotTruncated() {
    ReconciliationService capped = h.reconciliation(ledger, 2);
    seed(TxnStatus.FAILED);
    seed(TxnStatus.FAILED);

    ReconciliationResult result = capped.reconcile(FROM, TO);

    assertThat(result.truncated()).isFalse();
    assertThat(result.checked()).isEqualTo(2);
  }

  @Test
  void reportsEveryKindOfMismatchInTxnIdOrder() {
    SendMoneyTxn agree = seed(TxnStatus.COMPLETED);
    SendMoneyTxn flip = seed(TxnStatus.FAILED);
    SendMoneyTxn alert = seed(TxnStatus.COMPLETED);
    SendMoneyTxn unknown = seed(TxnStatus.FAILED);
    seed(TxnStatus.INITIATED);
    ledger.posted(agree.txnId(), OptionalLong.of(LEDGER_TS));
    ledger.posted(flip.txnId(), OptionalLong.of(LEDGER_TS));
    ledger.failLookup(unknown.txnId(), new LedgerUnavailableException("timeout"));

    ReconciliationResult result = service.reconcile(FROM, TO);

    assertThat(result.checked()).isEqualTo(5);
    assertThat(result.mismatches()).extracting(Mismatch::txnId, Mismatch::action).containsExactly(
        tuple(flip.txnId(), Action.FLIPPED_TO_COMPLETED),
        tuple(alert.txnId(), Action.ALERT_RAISED),
        tuple(unknown.txnId(), Action.NOT_CHECKED));
  }

  @Test
  void rowsOutsideTheWindowAreIgnored() {
    SendMoneyTxn inside = seed(TxnStatus.COMPLETED);

    ReconciliationResult result = service.reconcile(NOW.plusSeconds(10), NOW.plusSeconds(20));

    assertThat(result.checked()).isZero();
    assertThat(ledger.lookups()).extracting(Lookup::postingId).doesNotContain(inside.txnId());
  }
}
