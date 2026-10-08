package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.TxnFinaliser.Finalised;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Op;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Recheck;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Release;
import com.bracits.transactionservice.application.fakes.SendMoneyHarness;
import com.bracits.transactionservice.application.fakes.TxnRowBuilder;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.event.EventType;
import com.bracits.transactionservice.domain.event.SendMoneyEvent;
import com.bracits.transactionservice.domain.ledger.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.UnknownReason;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static com.bracits.transactionservice.application.fakes.Fixtures.LEDGER_TS;
import static com.bracits.transactionservice.application.fakes.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

class TxnFinaliserTest {

  private static final Duration DELAY = Duration.ofSeconds(2);

  private final SendMoneyHarness h = new SendMoneyHarness();
  private final TxnFinaliser finaliser = h.finaliser;

  private SendMoneyTxn seed(TxnRowBuilder row) {
    SendMoneyTxn built = row.build();
    h.txns.put(built);
    return built;
  }

  private SendMoneyTxn initiated() {
    return seed(TxnRowBuilder.row(h.ids.next()).nextCheckAt(Optional.of(NOW)));
  }

  @Test
  void postedCompletesAndPublishesAfterTheCommit() {
    UUID txnId = initiated().txnId();

    Finalised result = finaliser.finalise(txnId, new PostingOutcome.Posted(LEDGER_TS, false), DELAY);

    SendMoneyTxn stored = h.txns.get(txnId);
    assertThat(result).isEqualTo(new Finalised.Final(stored));
    assertThat(stored.status()).isEqualTo(TxnStatus.COMPLETED);
    assertThat(stored.ledgerTimestamp()).hasValue(LEDGER_TS);
    assertThat(stored.completedAt()).contains(NOW);
    assertThat(h.events.events()).containsExactly(h.eventMapper.toEvent(stored));
    assertThat(h.events.statusesAtPublish()).containsExactly(Optional.of(TxnStatus.COMPLETED));
  }

  @Test
  void rejectedFailsReleasesLimitsAndPublishes() {
    UUID txnId = initiated().txnId();

    Finalised result = finaliser.finalise(txnId, new PostingOutcome.Rejected(FailureCode.WALLET_NOT_FOUND, 2), DELAY);

    SendMoneyTxn stored = h.txns.get(txnId);
    assertThat(result).isEqualTo(new Finalised.Final(stored));
    assertThat(stored.failureCode()).contains(FailureCode.WALLET_NOT_FOUND);
    assertThat(h.txns.releases()).containsExactly(new Release(txnId, FailureCode.WALLET_NOT_FOUND));
    assertThat(h.events.events()).extracting(SendMoneyEvent::eventType).containsExactly(EventType.SEND_MONEY_FAILED);
  }

  @ParameterizedTest
  @EnumSource(UnknownReason.class)
  void unknownSchedulesARecheckAndPublishesNothing(UnknownReason reason) {
    UUID txnId = initiated().txnId();

    Finalised result = finaliser.finalise(txnId, new PostingOutcome.Unknown(reason), DELAY);

    assertThat(result).isEqualTo(new Finalised.InDoubt());
    assertThat(h.txns.rechecks()).containsExactly(new Recheck(txnId, DELAY));
    assertThat(h.txns.get(txnId).status()).isEqualTo(TxnStatus.INITIATED);
    assertThat(h.events.events()).isEmpty();
  }

  @Test
  void postedOnAnAlreadyCompletedRowReturnsItWithoutASecondEvent() {
    SendMoneyTxn done = seed(TxnRowBuilder.row(h.ids.next()).completed(9L, NOW.minusSeconds(1)));

    Finalised result = finaliser.finalise(done.txnId(), new PostingOutcome.Posted(LEDGER_TS, true), DELAY);

    assertThat(result).isEqualTo(new Finalised.Final(done));
    assertThat(h.events.events()).isEmpty();
  }

  @Test
  void rejectedOnAnAlreadyFailedRowReturnsItWithoutReleasingAgain() {
    SendMoneyTxn failed = seed(TxnRowBuilder.row(h.ids.next()).failed(FailureCode.INSUFFICIENT_FUNDS, NOW));

    Finalised result = finaliser.finalise(failed.txnId(),
        new PostingOutcome.Rejected(FailureCode.INSUFFICIENT_FUNDS, 1), DELAY);

    assertThat(result).isEqualTo(new Finalised.Final(failed));
    assertThat(h.txns.releases()).isEmpty();
    assertThat(h.events.events()).isEmpty();
  }

  @Test
  void missingRowIsInDoubt() {
    assertThat(finaliser.finalise(h.ids.next(), new PostingOutcome.Posted(LEDGER_TS, false), DELAY))
        .isEqualTo(new Finalised.InDoubt());
  }

  @Test
  void dataAccessFailureWhileCompletingIsInDoubt() {
    UUID txnId = initiated().txnId();
    h.txns.failNext(Op.MARK_COMPLETED, new DataAccessResourceFailureException("db down"));

    assertThat(finaliser.finalise(txnId, new PostingOutcome.Posted(LEDGER_TS, false), DELAY))
        .isEqualTo(new Finalised.InDoubt());
    assertThat(h.events.events()).isEmpty();
  }

  @Test
  void transactionFailureWhileFailingIsInDoubt() {
    UUID txnId = initiated().txnId();
    h.txns.failNext(Op.MARK_FAILED, new CannotCreateTransactionException("no connection"));

    assertThat(finaliser.finalise(txnId, new PostingOutcome.Rejected(FailureCode.INSUFFICIENT_FUNDS, 1), DELAY))
        .isEqualTo(new Finalised.InDoubt());
    assertThat(h.txns.get(txnId).status()).isEqualTo(TxnStatus.INITIATED);
  }

  @Test
  void dataAccessFailureWhileSchedulingTheRecheckIsInDoubt() {
    UUID txnId = initiated().txnId();
    h.txns.failNext(Op.SCHEDULE_RECHECK, new DataAccessResourceFailureException("db down"));

    assertThat(finaliser.finalise(txnId, new PostingOutcome.Unknown(UnknownReason.LEDGER_TIMEOUT), DELAY))
        .isEqualTo(new Finalised.InDoubt());
  }

  @Test
  void dataAccessFailureWhileReadingTheWinnersRowIsInDoubt() {
    SendMoneyTxn done = seed(TxnRowBuilder.row(h.ids.next()).completed(9L, NOW));
    h.txns.failNext(Op.FIND_BY_ID, new DataAccessResourceFailureException("db down"));

    assertThat(finaliser.finalise(done.txnId(), new PostingOutcome.Posted(LEDGER_TS, false), DELAY))
        .isEqualTo(new Finalised.InDoubt());
  }

  @Test
  void publishHandsTheMappedEventToThePublisherAndReturnsTheRow() {
    SendMoneyTxn done = seed(TxnRowBuilder.row(h.ids.next()).completed(LEDGER_TS, NOW));

    assertThat(finaliser.publish(done)).isSameAs(done);
    assertThat(h.events.events()).containsExactly(h.eventMapper.toEvent(done));
  }
}
