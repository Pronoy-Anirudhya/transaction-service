package com.bracits.transactionservice.adapter.out.jdbc;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.limit.LimitReservation;
import com.bracits.transactionservice.domain.txn.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers(disabledWithoutDocker = true)
class JdbcTxnRepositoryTest extends PostgresTestSupport {

  private static final LocalDate D = LocalDate.of(2026, 10, 8);
  private static final LocalDate OCT = LocalDate.of(2026, 10, 1);
  private static final long LEDGER_TS = 1_791_350_858_928_000_000L;
  private static final Duration NO_AGE = Duration.ZERO;
  private static final Duration LEASE = Duration.ofSeconds(30);

  private Wallet sender;
  private Wallet receiver;
  private int refSeq;

  @BeforeEach
  void createWallets() {
    sender = insertWallet(D);
    receiver = insertWallet(D);
  }

  /** DB transaction #1: insert + reserve, committed. */
  private NewSendMoneyTxn initiate(long amount, LocalDate businessDate) {
    NewSendMoneyTxn txn = newTxn(sender.walletId(), receiver.walletId(), "ref-" + ++refSeq, amount, businessDate);
    tx.executeWithoutResult(status -> {
      assertThat(txns.insertIfAbsent(txn)).contains(txn.txnId());
      assertThat(limits.reserve(new LimitReservation(sender.walletId(), businessDate, amount, TIER1))).isTrue();
    });
    return txn;
  }

  private static void backdateCreated(UUID txnId) {
    jdbc.sql("UPDATE send_money_txn SET created_at = created_at - interval '1 minute' WHERE txn_id = :id")
        .param("id", txnId).update();
  }

  private static void backdateCompleted(UUID txnId) {
    jdbc.sql("UPDATE send_money_txn SET completed_at = completed_at - interval '1 minute' WHERE txn_id = :id")
        .param("id", txnId).update();
  }

  @Nested
  class InsertAndFind {

    @Test
    void insertsAnInitiatedRowThatMapsBack() {
      NewSendMoneyTxn txn = newTxn(sender.walletId(), receiver.walletId(), "k-1", 100_000L, D);
      Instant before = Instant.now();

      assertThat(txns.insertIfAbsent(txn)).contains(txn.txnId());

      SendMoneyTxn row = txns.findById(txn.txnId()).orElseThrow();
      assertThat(row.clientRef()).isEqualTo("k-1");
      assertThat(row.requestHash()).isEqualTo(txn.requestHash());
      assertThat(row.senderWalletId()).isEqualTo(sender.walletId());
      assertThat(row.receiverWalletId()).isEqualTo(receiver.walletId());
      assertThat(row.amount()).isEqualTo(100_000L);
      assertThat(row.pricing()).isEqualTo(new Pricing(500L, 65L, 87L, 348L));
      assertThat(row.currency()).isEqualTo("BDT");
      assertThat(row.reference()).isEmpty();
      assertThat(row.businessDate()).isEqualTo(D);
      assertThat(row.status()).isEqualTo(TxnStatus.INITIATED);
      assertThat(row.failureCode()).isEmpty();
      assertThat(row.ledgerAttempts()).isZero();
      assertThat(row.nextCheckAt()).isPresent();
      assertThat(row.ledgerTimestamp()).isEmpty();
      assertThat(row.createdAt()).isCloseTo(before, within(5, ChronoUnit.SECONDS));
      assertThat(row.completedAt()).isEmpty();
      assertThat(row.eventPublishedAt()).isEmpty();
    }

    @Test
    void storesTheOptionalReference() {
      NewSendMoneyTxn base = newTxn(sender.walletId(), receiver.walletId(), "k-ref", 100_000L, D);
      NewSendMoneyTxn txn = new NewSendMoneyTxn(base.txnId(), base.clientRef(), base.requestHash(),
          base.senderWalletId(), base.receiverWalletId(), base.amount(), base.pricing(), base.currency(), "rent", D);

      txns.insertIfAbsent(txn);

      assertThat(txns.findById(txn.txnId()).orElseThrow().reference()).contains("rent");
    }

    @Test
    void duplicateSenderAndClientRefInsertsNothing() {
      NewSendMoneyTxn first = newTxn(sender.walletId(), receiver.walletId(), "dup", 100_000L, D);
      NewSendMoneyTxn second = newTxn(sender.walletId(), receiver.walletId(), "dup", 200_000L, D);
      txns.insertIfAbsent(first);

      assertThat(txns.insertIfAbsent(second)).isEmpty();
      assertThat(txns.findById(second.txnId())).isEmpty();
      assertThat(txns.findBySenderAndClientRef(sender.walletId(), "dup"))
          .get().extracting(SendMoneyTxn::txnId).isEqualTo(first.txnId());
      assertThat(txns.findBySenderAndClientRef(receiver.walletId(), "dup")).isEmpty();
    }
  }

  @Nested
  class Finalise {

    @Test
    void markCompletedIsCompareAndSet() {
      NewSendMoneyTxn txn = initiate(100_000L, D);

      SendMoneyTxn done = txns.markCompleted(txn.txnId(), LEDGER_TS).orElseThrow();

      assertThat(done.status()).isEqualTo(TxnStatus.COMPLETED);
      assertThat(done.ledgerTimestamp()).hasValue(LEDGER_TS);
      assertThat(done.completedAt()).isPresent();
      assertThat(txns.markCompleted(txn.txnId(), LEDGER_TS)).isEmpty();
      assertThat(txns.markFailedAndReleaseLimits(txn.txnId(), FailureCode.INSUFFICIENT_FUNDS)).isEmpty();
      assertThat(usage(sender.walletId())).isEqualTo(new Usage(D, 100_000L, 1, OCT, 100_000L, 1));
    }

    @Test
    void markFailedReleasesSameDayUsage() {
      NewSendMoneyTxn kept = initiate(70_000L, D);
      NewSendMoneyTxn failed = initiate(100_000L, D);

      SendMoneyTxn row = txns.markFailedAndReleaseLimits(failed.txnId(), FailureCode.INSUFFICIENT_FUNDS).orElseThrow();

      assertThat(row.status()).isEqualTo(TxnStatus.FAILED);
      assertThat(row.failureCode()).contains(FailureCode.INSUFFICIENT_FUNDS);
      assertThat(row.completedAt()).isPresent();
      assertThat(usage(sender.walletId())).isEqualTo(new Usage(D, 70_000L, 1, OCT, 70_000L, 1));
      assertThat(txns.markFailedAndReleaseLimits(failed.txnId(), FailureCode.INSUFFICIENT_FUNDS)).isEmpty();
      assertThat(usage(sender.walletId())).isEqualTo(new Usage(D, 70_000L, 1, OCT, 70_000L, 1));
      assertThat(txns.findById(kept.txnId()).orElseThrow().status()).isEqualTo(TxnStatus.INITIATED);
    }

    @Test
    void markFailedAfterMidnightReleasesOnlyTheMonth() {
      NewSendMoneyTxn yesterday = initiate(100_000L, D);
      initiate(30_000L, D.plusDays(1)); // usage row moves to the next day

      txns.markFailedAndReleaseLimits(yesterday.txnId(), FailureCode.INSUFFICIENT_FUNDS).orElseThrow();

      assertThat(usage(sender.walletId())).isEqualTo(new Usage(D.plusDays(1), 30_000L, 1, OCT, 30_000L, 1));
    }

    @Test
    void markFailedAfterMonthEndReleasesNothing() {
      LocalDate lastDay = LocalDate.of(2026, 10, 31);
      LocalDate nov = LocalDate.of(2026, 11, 1);
      NewSendMoneyTxn october = initiate(100_000L, lastDay);
      initiate(30_000L, nov);

      txns.markFailedAndReleaseLimits(october.txnId(), FailureCode.INSUFFICIENT_FUNDS).orElseThrow();

      assertThat(usage(sender.walletId())).isEqualTo(new Usage(nov, 30_000L, 1, nov, 30_000L, 1));
    }

    @Test
    void ledgerWinsFlipsFailedToCompletedAndRecounts() {
      NewSendMoneyTxn txn = initiate(100_000L, D);
      txns.markFailedAndReleaseLimits(txn.txnId(), FailureCode.INSUFFICIENT_FUNDS).orElseThrow();
      txns.markEventsPublished(List.of(txn.txnId()));
      assertThat(txns.markFailedAsCompleted(UUID.randomUUID(), OptionalLong.of(LEDGER_TS))).isEmpty();

      SendMoneyTxn row = txns.markFailedAsCompleted(txn.txnId(), OptionalLong.of(LEDGER_TS)).orElseThrow();

      assertThat(row.status()).isEqualTo(TxnStatus.COMPLETED);
      assertThat(row.failureCode()).isEmpty();
      assertThat(row.ledgerTimestamp()).hasValue(LEDGER_TS);
      assertThat(row.completedAt()).isPresent();
      assertThat(row.eventPublishedAt()).isEmpty();
      assertThat(usage(sender.walletId())).isEqualTo(new Usage(D, 100_000L, 1, OCT, 100_000L, 1));
      assertThat(txns.markFailedAsCompleted(txn.txnId(), OptionalLong.of(LEDGER_TS))).isEmpty();
      assertThat(usage(sender.walletId())).isEqualTo(new Usage(D, 100_000L, 1, OCT, 100_000L, 1));
    }

    @Test
    void ledgerWinsWithUnknownTimestampStoresNull() {
      NewSendMoneyTxn txn = initiate(100_000L, D);
      txns.markFailedAndReleaseLimits(txn.txnId(), FailureCode.LEDGER_REJECTED).orElseThrow();

      SendMoneyTxn row = txns.markFailedAsCompleted(txn.txnId(), OptionalLong.empty()).orElseThrow();

      assertThat(row.status()).isEqualTo(TxnStatus.COMPLETED);
      assertThat(row.ledgerTimestamp()).isEmpty();
    }

    @Test
    void ledgerWinsAfterMidnightRecountsOnlyTheMonth() {
      NewSendMoneyTxn txn = initiate(100_000L, D);
      txns.markFailedAndReleaseLimits(txn.txnId(), FailureCode.INSUFFICIENT_FUNDS).orElseThrow();
      initiate(30_000L, D.plusDays(1));

      txns.markFailedAsCompleted(txn.txnId(), OptionalLong.of(LEDGER_TS)).orElseThrow();

      assertThat(usage(sender.walletId())).isEqualTo(new Usage(D.plusDays(1), 30_000L, 1, OCT, 130_000L, 2));
    }

    @Test
    void ledgerWinsDoesNotTouchAnInitiatedRow() {
      NewSendMoneyTxn txn = initiate(100_000L, D);

      assertThat(txns.markFailedAsCompleted(txn.txnId(), OptionalLong.of(LEDGER_TS))).isEmpty();
      assertThat(txns.findById(txn.txnId()).orElseThrow().status()).isEqualTo(TxnStatus.INITIATED);
    }
  }

  @Nested
  class Repair {

    @Test
    void scheduleRecheckPushesNextCheckAndCountsTheAttempt() {
      NewSendMoneyTxn txn = initiate(100_000L, D);
      Instant before = Instant.now();

      assertThat(txns.scheduleRecheck(txn.txnId(), Duration.ofSeconds(2))).isTrue();
      assertThat(txns.scheduleRecheck(txn.txnId(), Duration.ofSeconds(2))).isTrue();

      SendMoneyTxn row = txns.findById(txn.txnId()).orElseThrow();
      assertThat(row.ledgerAttempts()).isEqualTo(2);
      assertThat(row.nextCheckAt().orElseThrow()).isCloseTo(before.plusSeconds(2), within(3, ChronoUnit.SECONDS));

      txns.markCompleted(txn.txnId(), LEDGER_TS);
      assertThat(txns.scheduleRecheck(txn.txnId(), Duration.ofSeconds(2))).isFalse();
    }

    @Test
    void claimInDoubtRespectsMinAge() {
      NewSendMoneyTxn txn = initiate(100_000L, D);

      assertThat(txns.claimInDoubt(10, Duration.ofSeconds(3), LEASE)).isEmpty();

      backdateCreated(txn.txnId());
      assertThat(txns.claimInDoubt(10, Duration.ofSeconds(3), LEASE))
          .extracting(SendMoneyTxn::txnId).containsExactly(txn.txnId());
    }

    @Test
    void claimInDoubtRespectsNextCheckAt() {
      NewSendMoneyTxn txn = initiate(100_000L, D);
      backdateCreated(txn.txnId());
      txns.scheduleRecheck(txn.txnId(), Duration.ofMinutes(5));

      assertThat(txns.claimInDoubt(10, NO_AGE, LEASE)).isEmpty();
    }

    @Test
    void claimInDoubtLeasesTheRows() {
      NewSendMoneyTxn txn = initiate(100_000L, D);
      backdateCreated(txn.txnId());
      Instant before = Instant.now();

      List<SendMoneyTxn> claimed = txns.claimInDoubt(10, NO_AGE, LEASE);

      assertThat(claimed).hasSize(1);
      assertThat(claimed.getFirst().nextCheckAt().orElseThrow())
          .isCloseTo(before.plus(LEASE), within(5, ChronoUnit.SECONDS));
      assertThat(txns.claimInDoubt(10, NO_AGE, LEASE)).isEmpty();
    }

    @Test
    void claimInDoubtHonoursTheLimitAndSkipsFinalRows() {
      NewSendMoneyTxn a = initiate(1_000L, D);
      NewSendMoneyTxn b = initiate(1_000L, D);
      NewSendMoneyTxn done = initiate(1_000L, D);
      List.of(a, b, done).forEach(t -> backdateCreated(t.txnId()));
      txns.markCompleted(done.txnId(), LEDGER_TS);

      assertThat(txns.claimInDoubt(1, NO_AGE, LEASE)).hasSize(1);
      assertThat(txns.claimInDoubt(10, NO_AGE, LEASE)).hasSize(1);
      assertThat(txns.claimInDoubt(10, NO_AGE, LEASE)).isEmpty();
    }

    @Test
    void claimInDoubtSkipsLockedRows() {
      NewSendMoneyTxn locked = initiate(1_000L, D);
      NewSendMoneyTxn free = initiate(1_000L, D);
      backdateCreated(locked.txnId());
      backdateCreated(free.txnId());

      List<SendMoneyTxn> claimed = tx.execute(status -> {
        jdbc.sql("SELECT txn_id FROM send_money_txn WHERE txn_id = :id FOR UPDATE")
            .param("id", locked.txnId()).query(UUID.class).single();
        // another connection, while this transaction holds the row lock
        return CompletableFuture.supplyAsync(() -> txns.claimInDoubt(10, NO_AGE, LEASE)).join();
      });

      assertThat(claimed).extracting(SendMoneyTxn::txnId).containsExactly(free.txnId());
      assertThat(txns.claimInDoubt(10, NO_AGE, LEASE))
          .extracting(SendMoneyTxn::txnId).containsExactly(locked.txnId());
    }

    @Test
    void countInDoubtCountsInitiatedRows() {
      initiate(1_000L, D);
      NewSendMoneyTxn done = initiate(1_000L, D);
      txns.markCompleted(done.txnId(), LEDGER_TS);

      assertThat(txns.countInDoubt()).isEqualTo(1L);
    }
  }

  @Nested
  class Publishing {

    @Test
    void claimUnpublishedThenMarkPublished() {
      NewSendMoneyTxn completed = initiate(1_000L, D);
      NewSendMoneyTxn failed = initiate(1_000L, D);
      NewSendMoneyTxn inFlight = initiate(1_000L, D);
      txns.markCompleted(completed.txnId(), LEDGER_TS);
      txns.markFailedAndReleaseLimits(failed.txnId(), FailureCode.INSUFFICIENT_FUNDS);
      assertThat(txns.countUnpublished()).isEqualTo(2L);

      assertThat(txns.claimUnpublished(10, Duration.ofSeconds(10), LEASE)).isEmpty(); // too young

      List.of(completed, failed, inFlight).forEach(t -> backdateCompleted(t.txnId()));
      assertThat(txns.claimUnpublished(10, Duration.ofSeconds(10), LEASE))
          .extracting(SendMoneyTxn::txnId).containsExactlyInAnyOrder(completed.txnId(), failed.txnId());
      assertThat(txns.claimUnpublished(10, Duration.ofSeconds(10), LEASE)).isEmpty(); // leased

      assertThat(txns.markEventsPublished(List.of(completed.txnId(), failed.txnId()))).isEqualTo(2);
      assertThat(txns.countUnpublished()).isZero();
      assertThat(txns.findById(completed.txnId()).orElseThrow().eventPublishedAt()).isPresent();
      assertThat(txns.markEventsPublished(List.of(completed.txnId()))).isZero();
      assertThat(txns.findById(inFlight.txnId()).orElseThrow().eventPublishedAt()).isEmpty();
    }

    @Test
    void markEventsPublishedWithNoIdsIsANoOp() {
      assertThat(txns.markEventsPublished(List.of())).isZero();
    }

    @Test
    void claimUnpublishedHonoursTheLimit() {
      for (int i = 0; i < 3; i++) {
        NewSendMoneyTxn txn = initiate(1_000L, D);
        txns.markCompleted(txn.txnId(), LEDGER_TS);
        backdateCompleted(txn.txnId());
      }

      assertThat(txns.claimUnpublished(2, Duration.ofSeconds(10), LEASE)).hasSize(2);
      assertThat(txns.claimUnpublished(2, Duration.ofSeconds(10), LEASE)).hasSize(1);
    }
  }

  @Nested
  class Reconciliation {

    @Test
    void findByTxnIdRangeScansInPrimaryKeyOrder() {
      List<UUID> ids = List.of(timeOrdered(1), timeOrdered(2), timeOrdered(3), timeOrdered(4));
      for (UUID id : ids.reversed()) {
        NewSendMoneyTxn base = newTxn(sender.walletId(), receiver.walletId(), "range-" + id, 1_000L, D);
        txns.insertIfAbsent(new NewSendMoneyTxn(id, base.clientRef(), base.requestHash(), base.senderWalletId(),
            base.receiverWalletId(), base.amount(), base.pricing(), base.currency(), null, D));
      }

      assertThat(txns.findByTxnIdRange(ids.get(1), ids.get(3), 10))
          .extracting(SendMoneyTxn::txnId).containsExactly(ids.get(1), ids.get(2));
      assertThat(txns.findByTxnIdRange(ids.get(0), timeOrdered(9), 3))
          .extracting(SendMoneyTxn::txnId).containsExactly(ids.get(0), ids.get(1), ids.get(2));
    }

    private static UUID timeOrdered(long millis) {
      return new UUID((0x0192_f5a4_0000L + millis) << 16, 0x0102_0304_0506_0700L);
    }
  }
}
