package com.bracits.transactionservice.application.sendmoney.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bracits.transactionservice.application.enums.ReservationOutcome;
import com.bracits.transactionservice.application.sendmoney.service.impl.LimitReserverImpl;
import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.port.out.repository.LimitRepository;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class LimitReserverTest {

  private static final UUID TXN_ID = UUID.fromString("01a11a3f-e349-d824-d395-04d2cb380100");
  private static final NewSendMoneyTxn TXN = new NewSendMoneyTxn(TXN_ID, "key-1",
      new RequestHash(new byte[32]),
      1L, 2L, 100_000L, new Pricing(500, 65, 87, 348), "BDT", null, LocalDate.parse("2026-10-07"));
  private static final LimitReservation RESERVATION = new LimitReservation(1L, TXN.businessDate(),
      100_000L, null);

  private final TxnRepository txns = mock(TxnRepository.class);
  private final LimitRepository limits = mock(LimitRepository.class);
  private final RecordingTransactions transactions = new RecordingTransactions();
  private final LimitReserver reserver = new LimitReserverImpl(txns, limits, transactions);

  @Test
  void insertAndReserveCommit() {
    given(txns.insertIfAbsent(TXN)).willReturn(Optional.of(TXN_ID));
    given(limits.reserve(RESERVATION)).willReturn(true);

    assertThat(reserver.reserve(TXN, RESERVATION)).isEqualTo(ReservationOutcome.RESERVED);
    assertThat(transactions.status.isRollbackOnly()).isFalse();
  }

  @Test
  void duplicateSkipsTheLimitUpdate() {
    given(txns.insertIfAbsent(any())).willReturn(Optional.empty());

    assertThat(reserver.reserve(TXN, RESERVATION)).isEqualTo(ReservationOutcome.DUPLICATE);
    verifyNoInteractions(limits);
  }

  @Test
  void exceededLimitRollsTheInsertBack() {
    given(txns.insertIfAbsent(TXN)).willReturn(Optional.of(TXN_ID));
    given(limits.reserve(RESERVATION)).willReturn(false);

    assertThat(reserver.reserve(TXN, RESERVATION)).isEqualTo(ReservationOutcome.LIMIT_EXCEEDED);
    assertThat(transactions.status.isRollbackOnly()).isTrue();
  }

  /**
   * Runs the callback in-line and keeps the status so rollback-only can be asserted.
   */
  private static final class RecordingTransactions implements TransactionOperations {

    private final SimpleTransactionStatus status = new SimpleTransactionStatus();

    @Override
    public <T> T execute(TransactionCallback<T> action) {
      return action.doInTransaction(status);
    }
  }
}
