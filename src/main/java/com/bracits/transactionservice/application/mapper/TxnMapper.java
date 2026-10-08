package com.bracits.transactionservice.application.mapper;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.result.TxnSummary;
import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Conversions between the Send Money command, the record to insert, stored rows and the response
 * summary.
 */
public interface TxnMapper {

  NewSendMoneyTxn toNewTxn(
      UUID txnId,
      SendMoneyCommand command,
      RequestHash hash,
      Wallet sender,
      Wallet receiver,
      Pricing pricing,
      LocalDate businessDate);

  TxnSummary toSummary(SendMoneyTxn txn);

  /**
   * Summary of a just-inserted row whose ledger outcome is still unknown.
   */
  TxnSummary toInitiatedSummary(NewSendMoneyTxn txn);

  /**
   * The limit reservation for the sender of a new transaction (spec 6.1).
   */
  LimitReservation toLimitReservation(NewSendMoneyTxn txn, LimitRule rule);

  /**
   * The use-case result of a stored row: INITIATED → 202, COMPLETED → 200, FAILED → 422 with its
   * code.
   */
  SendMoneyResult toResult(SendMoneyTxn row);
}
