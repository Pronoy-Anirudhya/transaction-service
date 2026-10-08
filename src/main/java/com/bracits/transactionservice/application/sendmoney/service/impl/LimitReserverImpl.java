package com.bracits.transactionservice.application.sendmoney.service.impl;

import com.bracits.transactionservice.application.enums.ReservationOutcome;
import com.bracits.transactionservice.application.sendmoney.service.LimitReserver;
import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import com.bracits.transactionservice.port.out.repository.LimitRepository;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Default implementation of {@link LimitReserver}.
 */
@Component
public final class LimitReserverImpl implements LimitReserver {

  private final TxnRepository txns;
  private final LimitRepository limits;
  private final TransactionOperations transactions;

  public LimitReserverImpl(TxnRepository txns, LimitRepository limits,
      TransactionOperations transactions) {
    this.txns = txns;
    this.limits = limits;
    this.transactions = transactions;
  }

  @Override
  public ReservationOutcome reserve(NewSendMoneyTxn txn, LimitReservation reservation) {
    return Objects.requireNonNull(transactions.execute(status -> {
      if (txns.insertIfAbsent(txn).isEmpty()) {
        return ReservationOutcome.DUPLICATE;
      }

      if (!limits.reserve(reservation)) {
        status.setRollbackOnly();
        return ReservationOutcome.LIMIT_EXCEEDED;
      }

      return ReservationOutcome.RESERVED;
    }));
  }
}
