package com.bracits.transactionservice.application.mapper;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.result.TxnSummary;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.txn.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.RequestHash;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.Wallet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Conversions between the Send Money command, the record to insert, stored rows and the response summary. */
@Component
public final class TxnMapper {

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

  public TxnSummary toSummary(SendMoneyTxn txn) {
    return new TxnSummary(
        txn.txnId(), txn.status(), txn.amount(), txn.pricing(), txn.failureCode(), txn.completedAt());
  }

  /** Summary of a just-inserted row whose ledger outcome is still unknown. */
  public TxnSummary toInitiatedSummary(NewSendMoneyTxn txn) {
    return new TxnSummary(
        txn.txnId(), TxnStatus.INITIATED, txn.amount(), txn.pricing(), Optional.empty(), Optional.empty());
  }
}
