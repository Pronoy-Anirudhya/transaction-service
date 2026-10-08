package com.bracits.transactionservice.application.mapper.impl;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.mapper.TxnMapper;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.result.TxnSummary;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link TxnMapper}.
 */
@Component
public final class TxnMapperImpl implements TxnMapper {

  @Override
  public NewSendMoneyTxn toNewTxn(
      UUID txnId,
      SendMoneyCommand command,
      RequestHash hash,
      Wallet sender,
      Wallet receiver,
      Pricing pricing,
      LocalDate businessDate) {
    return new NewSendMoneyTxn(
        txnId,
        command.idempotencyKey(),
        hash,
        sender.walletId(),
        receiver.walletId(),
        command.amount(),
        pricing,
        command.currency(),
        command.reference().orElse(null),
        businessDate);
  }

  @Override
  public TxnSummary toSummary(SendMoneyTxn txn) {
    return new TxnSummary(
        txn.txnId(), txn.status(), txn.amount(), txn.pricing(), txn.failureCode(),
        txn.completedAt());
  }

  /**
   * Summary of a just-inserted row whose ledger outcome is still unknown.
   */
  @Override
  public TxnSummary toInitiatedSummary(NewSendMoneyTxn txn) {
    return new TxnSummary(
        txn.txnId(), TxnStatus.INITIATED, txn.amount(), txn.pricing(), Optional.empty(),
        Optional.empty());
  }

  /**
   * The limit reservation for the sender of a new transaction (spec 6.1).
   */
  @Override
  public LimitReservation toLimitReservation(NewSendMoneyTxn txn, LimitRule rule) {
    return new LimitReservation(txn.senderWalletId(), txn.businessDate(), txn.amount(), rule);
  }

  /**
   * The use-case result of a stored row: INITIATED → 202, COMPLETED → 200, FAILED → 422 with its
   * code.
   */
  @Override
  public SendMoneyResult toResult(SendMoneyTxn row) {
    return switch (row.status()) {
      case INITIATED -> new SendMoneyResult.Processing(toSummary(row));
      case COMPLETED -> new SendMoneyResult.Completed(toSummary(row));
      case FAILED -> new SendMoneyResult.Rejected(
          row.failureCode().orElse(FailureCode.LEDGER_REJECTED), Optional.of(row.txnId()));
    };
  }
}
