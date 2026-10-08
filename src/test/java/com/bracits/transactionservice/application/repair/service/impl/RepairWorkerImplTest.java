package com.bracits.transactionservice.application.repair.service.impl;

import static com.bracits.transactionservice.application.fakes.Fixtures.AMOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.LEDGER_TS;
import static com.bracits.transactionservice.application.fakes.Fixtures.NOW;
import static com.bracits.transactionservice.application.fakes.Fixtures.RECEIVER_ID;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_ID;
import static com.bracits.transactionservice.application.fakes.Fixtures.STANDARD_PRICING;
import static com.bracits.transactionservice.application.fakes.Fixtures.command;
import static com.bracits.transactionservice.application.fakes.Fixtures.receiver;
import static com.bracits.transactionservice.application.fakes.Fixtures.sender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Claim;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Op;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Recheck;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Release;
import com.bracits.transactionservice.application.fakes.LogCapture;
import com.bracits.transactionservice.application.fakes.SendMoneyHarness;
import com.bracits.transactionservice.application.fakes.TxnRowBuilder;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import com.bracits.transactionservice.domain.event.enums.EventType;
import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;
import com.bracits.transactionservice.domain.ledger.enums.UnknownReason;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.resilience.InvocationRejectedException;

class RepairWorkerImplTest {

  private static final PostingOutcome POSTED = new PostingOutcome.Posted(LEDGER_TS, true);

  private final SendMoneyHarness h = new SendMoneyHarness();
  private final RepairWorkerImpl worker = h.repairWorker;

  /**
   * An in-doubt row older than the minimum age and due for a recheck.
   */
  private SendMoneyTxn seedInDoubt(int ledgerAttempts) {
    SendMoneyTxn row = TxnRowBuilder.row(h.ids.next())
        .clientRef("key-" + h.ids.issued().size())
        .ledgerAttempts(ledgerAttempts)
        .createdAt(NOW.minusSeconds(10))
        .nextCheckAt(Optional.of(NOW.minusSeconds(1)))
        .build();

    h.txns.put(row);
    return row;
  }

  private double repairCount(String outcome) {
    var counter = h.registry.find("txn.repair.attempts").tag("outcome", outcome).counter();
    return counter == null ? 0.0 : counter.count();
  }

  @Test
  void claimsWithTheConfiguredBatchMinAgeAndLease() {
    worker.run();

    assertThat(h.txns.claims()).containsExactly(
        new Claim(200, Duration.ofSeconds(3), Duration.ofSeconds(30)));
  }

  @Test
  void resendsTheIdenticalPostingForTheRow() {
    SendMoneyTxn row = seedInDoubt(0);
    h.ledger.thenReturn(POSTED);

    worker.run();

    assertThat(h.ledger.requests())
        .containsExactly(
            h.legPlanner.plan(row.txnId(), sender(), receiver(), AMOUNT, STANDARD_PRICING));
  }

  @Test
  void resendIsIdenticalToTheRequestPathsPosting() {
    h.ledger.thenReturn(new PostingOutcome.Unknown(UnknownReason.LEDGER_TIMEOUT))
        .thenReturn(POSTED);
    h.service.send(command());
    h.clock.advance(Duration.ofSeconds(5));

    worker.run();

    assertThat(h.ledger.requests()).hasSize(2);
    assertThat(h.ledger.requests().get(1)).isEqualTo(h.ledger.requests().get(0));
    assertThat(h.txns.all()).singleElement().extracting(SendMoneyTxn::status)
        .isEqualTo(TxnStatus.COMPLETED);
  }

  @Test
  void postedCompletesTheRowAndPublishesTheEvent() {
    SendMoneyTxn row = seedInDoubt(1);
    h.ledger.thenReturn(POSTED);

    worker.run();

    SendMoneyTxn stored = h.txns.get(row.txnId());
    assertThat(stored.status()).isEqualTo(TxnStatus.COMPLETED);
    assertThat(stored.ledgerTimestamp()).hasValue(LEDGER_TS);
    assertThat(h.events.events()).singleElement().satisfies(event -> {
      assertThat(event.txnId()).isEqualTo(row.txnId());
      assertThat(event.eventType()).isEqualTo(EventType.SEND_MONEY_COMPLETED);
    });
    assertThat(h.events.statusesAtPublish()).containsExactly(Optional.of(TxnStatus.COMPLETED));
    assertThat(repairCount("completed")).isEqualTo(1.0);
  }

  @Test
  void rejectedFailsTheRowReleasesLimitsAndPublishesTheEvent() {
    SendMoneyTxn row = seedInDoubt(1);
    h.ledger.thenReturn(new PostingOutcome.Rejected(FailureCode.INSUFFICIENT_FUNDS, 1));

    worker.run();

    assertThat(h.txns.get(row.txnId()).status()).isEqualTo(TxnStatus.FAILED);
    assertThat(h.txns.get(row.txnId()).failureCode()).contains(FailureCode.INSUFFICIENT_FUNDS);
    assertThat(h.txns.releases()).containsExactly(
        new Release(row.txnId(), FailureCode.INSUFFICIENT_FUNDS));
    assertThat(h.events.events()).extracting(SendMoneyEvent::eventType)
        .containsExactly(EventType.SEND_MONEY_FAILED);
    assertThat(repairCount("failed")).isEqualTo(1.0);
  }

  @ParameterizedTest
  @CsvSource({"0, 1", "1, 2", "2, 4", "3, 8", "5, 32", "6, 60", "9, 60", "40, 60"})
  void unknownIsRecheckedWithExponentialBackoff(int attempts, long expectedSeconds) {
    SendMoneyTxn row = seedInDoubt(attempts);
    h.ledger.thenReturn(new PostingOutcome.Unknown(UnknownReason.LEDGER_TIMEOUT));

    worker.run();

    assertThat(h.txns.rechecks()).containsExactly(
        new Recheck(row.txnId(), Duration.ofSeconds(expectedSeconds)));
    SendMoneyTxn stored = h.txns.get(row.txnId());
    assertThat(stored.status()).isEqualTo(TxnStatus.INITIATED);
    assertThat(stored.ledgerAttempts()).isEqualTo(attempts + 1);
    assertThat(h.events.events()).isEmpty();
    assertThat(repairCount("in_doubt")).isEqualTo(1.0);
  }

  @ParameterizedTest
  @CsvSource({"0, 1", "1, 2", "2, 4", "3, 8", "4, 16", "5, 32", "6, 60", "7, 60", "30, 60",
      "31, 60",
      "62, 60", "63, 60", "64, 60", "2147483647, 60", "-1, 1", "-2147483648, 1"})
  void backoffDoublesFromOneSecondAndIsCappedAtSixty(int attempts, long expectedSeconds) {
    assertThat(worker.backoff(attempts)).isEqualTo(Duration.ofSeconds(expectedSeconds));
  }

  @ParameterizedTest
  @EnumSource(UnknownReason.class)
  void neverFailsARowWithoutADefinitiveRejection(UnknownReason reason) {
    SendMoneyTxn row = seedInDoubt(3);
    h.ledger.thenReturn(new PostingOutcome.Unknown(reason));

    worker.run();

    assertThat(h.txns.get(row.txnId()).status()).isEqualTo(TxnStatus.INITIATED);
    assertThat(h.txns.releases()).isEmpty();
  }

  @Test
  void ledgerExceptionsLeaveTheRowInitiatedWithARecheck() {
    SendMoneyTxn overloaded = seedInDoubt(0);
    SendMoneyTxn broken = seedInDoubt(0);
    h.ledger.otherwise(request -> {
      if (request.postingId().equals(overloaded.txnId())) {
        throw new InvocationRejectedException("ledger bulkhead full", new Object());
      }
      throw new IllegalStateException("boom");
    });

    worker.run();

    assertThat(h.txns.get(overloaded.txnId()).status()).isEqualTo(TxnStatus.INITIATED);
    assertThat(h.txns.get(broken.txnId()).status()).isEqualTo(TxnStatus.INITIATED);
    assertThat(h.txns.rechecks()).containsExactlyInAnyOrder(
        new Recheck(overloaded.txnId(), Duration.ofSeconds(1)),
        new Recheck(broken.txnId(), Duration.ofSeconds(1)));
    assertThat(h.events.events()).isEmpty();
  }

  @Test
  void missingReceiverWalletIsRecheckedAtMaxBackoffWithoutALedgerCall() {
    SendMoneyTxn row = seedInDoubt(0);
    h.wallets.remove(RECEIVER_ID);

    try (LogCapture log = LogCapture.of(RepairWorkerImpl.class)) {
      worker.run();

      assertThat(log.messages(Level.ERROR)).anySatisfy(
          m -> assertThat(m).contains("wallet " + RECEIVER_ID));
    }

    assertThat(h.ledger.requests()).isEmpty();
    assertThat(h.txns.rechecks()).containsExactly(new Recheck(row.txnId(), Duration.ofSeconds(60)));
    assertThat(h.txns.get(row.txnId()).status()).isEqualTo(TxnStatus.INITIATED);
  }

  @Test
  void missingSenderWalletIsRecheckedAtMaxBackoffWithoutALedgerCall() {
    SendMoneyTxn row = seedInDoubt(0);
    h.wallets.remove(SENDER_ID);

    worker.run();

    assertThat(h.ledger.requests()).isEmpty();
    assertThat(h.txns.rechecks()).containsExactly(new Recheck(row.txnId(), Duration.ofSeconds(60)));
  }

  @Test
  void alertsOnceTheAttemptThresholdIsReached() {
    seedInDoubt(9);
    h.ledger.thenReturn(new PostingOutcome.Unknown(UnknownReason.LEDGER_TIMEOUT));

    try (LogCapture log = LogCapture.of(RepairWorkerImpl.class)) {
      worker.run();

      assertThat(log.templates(Level.ERROR)).containsExactly(
          ApplicationConstants.ALERT_REPAIR_ATTEMPTS);
      assertThat(log.messages(Level.ERROR)).singleElement().asString()
          .contains("after 10 ledger attempts");
    }
  }

  @Test
  void doesNotAlertBelowTheThreshold() {
    seedInDoubt(8);
    h.ledger.thenReturn(new PostingOutcome.Unknown(UnknownReason.LEDGER_TIMEOUT));

    try (LogCapture log = LogCapture.of(RepairWorkerImpl.class)) {
      worker.run();

      assertThat(log.templates(Level.ERROR)).doesNotContain(
          ApplicationConstants.ALERT_REPAIR_ATTEMPTS);
    }
  }

  @Test
  void nothingClaimedMakesNoLedgerCall() {
    worker.run();

    assertThat(h.ledger.requests()).isEmpty();
    assertThat(h.registry.find("txn.repair.attempts").counters()).isEmpty();
  }

  @Test
  void rowsNotYetDueOrTooYoungAreNotClaimed() {
    h.txns.put(TxnRowBuilder.row(h.ids.next()).clientRef("young")
        .createdAt(NOW.minusSeconds(1)).nextCheckAt(Optional.of(NOW.minusSeconds(1))).build());
    h.txns.put(TxnRowBuilder.row(h.ids.next()).clientRef("leased")
        .createdAt(NOW.minusSeconds(60)).nextCheckAt(Optional.of(NOW.plusSeconds(20))).build());

    worker.run();

    assertThat(h.ledger.requests()).isEmpty();
  }

  @Test
  void claimFailureIsSwallowed() {
    seedInDoubt(0);
    h.txns.failNext(Op.CLAIM_IN_DOUBT, new DataAccessResourceFailureException("db down"));

    assertThatCode(worker::run).doesNotThrowAnyException();
    assertThat(h.ledger.requests()).isEmpty();
  }

  @Test
  void finalisationFailureLeavesTheRowForTheNextLease() {
    SendMoneyTxn row = seedInDoubt(0);
    h.ledger.thenReturn(POSTED);
    h.txns.failNext(Op.MARK_COMPLETED, new DataAccessResourceFailureException("db down"));

    assertThatCode(worker::run).doesNotThrowAnyException();

    assertThat(h.txns.get(row.txnId()).status()).isEqualTo(TxnStatus.INITIATED);
    assertThat(h.events.events()).isEmpty();
    assertThat(repairCount("in_doubt")).isEqualTo(1.0);
  }

  @Test
  void oneFailingRowDoesNotStopTheBatch() {
    SendMoneyTxn orphan = TxnRowBuilder.from(seedInDoubt(0)).receiverWalletId(999L).build();
    h.txns.put(orphan);
    SendMoneyTxn healthy = seedInDoubt(0);
    h.txns.failNext(Op.SCHEDULE_RECHECK, new DataAccessResourceFailureException("db down"));
    h.ledger.otherwise(request -> POSTED);

    assertThatCode(worker::run).doesNotThrowAnyException();

    assertThat(h.txns.get(healthy.txnId()).status()).isEqualTo(TxnStatus.COMPLETED);
    assertThat(h.txns.get(orphan.txnId()).status()).isEqualTo(TxnStatus.INITIATED);
  }

  @Test
  void processesEveryClaimedRow() {
    UUID a = seedInDoubt(0).txnId();
    UUID b = seedInDoubt(2).txnId();
    UUID c = seedInDoubt(4).txnId();
    h.ledger.otherwise(request -> POSTED);

    worker.run();

    assertThat(h.txns.all()).extracting(SendMoneyTxn::status).containsOnly(TxnStatus.COMPLETED);
    assertThat(h.events.events()).extracting(SendMoneyEvent::txnId)
        .containsExactlyInAnyOrder(a, b, c);
    assertThat(repairCount("completed")).isEqualTo(3.0);
  }

  @Test
  void rowFinalisedConcurrentlyIsReportedWithoutASecondEvent() {
    SendMoneyTxn row = seedInDoubt(0);
    h.ledger.thenAnswer(request -> {
      h.txns.markCompleted(request.postingId(), 5L);
      return POSTED;
    });

    worker.run();

    assertThat(h.txns.get(row.txnId()).ledgerTimestamp()).hasValue(5L);
    assertThat(h.events.events()).isEmpty();
    assertThat(repairCount("completed")).isEqualTo(1.0);
  }

  @Test
  void pausesWhileTheLedgerIsDown() {
    h.ledgerHealth.down();

    worker.run();

    assertThat(h.txns.claims()).isEmpty();
    assertThat(h.ledger.requests()).isEmpty();
  }
}
