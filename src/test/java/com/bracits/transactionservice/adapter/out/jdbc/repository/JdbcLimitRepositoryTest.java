package com.bracits.transactionservice.adapter.out.jdbc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcLimitRepositoryTest extends PostgresTestSupport {

  private static final LocalDate D = LocalDate.of(2026, 10, 8);
  private static final LocalDate OCT = LocalDate.of(2026, 10, 1);

  private Wallet sender;

  @BeforeEach
  void createSender() {
    sender = insertWallet(D);
  }

  private boolean reserve(LocalDate day, long amount) {
    return limits.reserve(new LimitReservation(sender.walletId(), day, amount, TIER1));
  }

  @Test
  void reservesWithinLimits() {
    assertThat(reserve(D, 100_000L)).isTrue();
    assertThat(reserve(D, 50_000L)).isTrue();

    assertThat(usage(sender.walletId())).isEqualTo(new Usage(D, 150_000L, 2, OCT, 150_000L, 2));
  }

  @Test
  void reservesUpToTheDailyAmountExactly() {
    setUsage(sender.walletId(), new Usage(D, 4_900_000L, 3, OCT, 4_900_000L, 3));

    assertThat(reserve(D, 100_000L)).isTrue();
    assertThat(usage(sender.walletId()).dayAmount()).isEqualTo(5_000_000L);
  }

  @Test
  void rejectsOverDailyAmountAndLeavesCountersUntouched() {
    Usage before = new Usage(D, 4_990_000L, 3, OCT, 4_990_000L, 3);
    setUsage(sender.walletId(), before);

    assertThat(reserve(D, 20_000L)).isFalse();
    assertThat(usage(sender.walletId())).isEqualTo(before);
  }

  @Test
  void rejectsOverDailyCount() {
    Usage before = new Usage(D, 50_000L, 50, OCT, 50_000L, 50);
    setUsage(sender.walletId(), before);

    assertThat(reserve(D, 1_000L)).isFalse();
    assertThat(usage(sender.walletId())).isEqualTo(before);
  }

  @Test
  void rejectsOverMonthlyAmount() {
    Usage before = new Usage(D.minusDays(1), 0L, 0, OCT, 29_990_000L, 100);
    setUsage(sender.walletId(), before);

    assertThat(reserve(D, 20_000L)).isFalse();
    assertThat(usage(sender.walletId())).isEqualTo(before);
  }

  @Test
  void rejectsOverMonthlyCount() {
    Usage before = new Usage(D.minusDays(1), 10_000L, 10, OCT, 1_000_000L, 200);
    setUsage(sender.walletId(), before);

    assertThat(reserve(D, 1_000L)).isFalse();
    assertThat(usage(sender.walletId())).isEqualTo(before);
  }

  @Test
  void dayRolloverResetsDailyCountersInPlace() {
    // yesterday the daily limits were exhausted; the month continues
    setUsage(sender.walletId(), new Usage(D.minusDays(1), 5_000_000L, 50, OCT, 6_000_000L, 60));

    assertThat(reserve(D, 100_000L)).isTrue();
    assertThat(usage(sender.walletId())).isEqualTo(new Usage(D, 100_000L, 1, OCT, 6_100_000L, 61));
  }

  @Test
  void monthRolloverResetsDailyAndMonthlyCountersInPlace() {
    setUsage(sender.walletId(),
        new Usage(LocalDate.of(2026, 9, 30), 5_000_000L, 50, LocalDate.of(2026, 9, 1), 30_000_000L,
            200));

    assertThat(reserve(OCT, 100_000L)).isTrue();
    assertThat(usage(sender.walletId())).isEqualTo(new Usage(OCT, 100_000L, 1, OCT, 100_000L, 1));
  }

  @Test
  void unknownWalletReservesNothing() {
    assertThat(limits.reserve(new LimitReservation(999_999L, D, 1_000L, TIER1))).isFalse();
  }

  @Test
  void reservationIsRolledBackWithTheSurroundingTransaction() {
    Wallet receiver = insertWallet(D);
    NewSendMoneyTxn txn = newTxn(sender.walletId(), receiver.walletId(), "rollback-1", 100_000L, D);

    tx.executeWithoutResult(status -> {
      assertThat(txns.insertIfAbsent(txn)).contains(txn.txnId());
      assertThat(reserve(D, txn.amount())).isTrue();
      status.setRollbackOnly();
    });

    assertThat(txns.findById(txn.txnId())).isEmpty();
    assertThat(usage(sender.walletId())).isEqualTo(new Usage(D, 0L, 0, OCT, 0L, 0));
  }
}
