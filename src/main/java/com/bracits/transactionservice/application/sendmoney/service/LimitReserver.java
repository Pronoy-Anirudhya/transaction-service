package com.bracits.transactionservice.application.sendmoney.service;

import com.bracits.transactionservice.application.enums.ReservationOutcome;
import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;

/**
 * Spec 5 step 6, DB transaction #1: insert the INITIATED row and reserve the sender's limits in one
 * short transaction. The insert is rolled back when a limit would be exceeded, so the row is
 * durable before the ledger is called and limits can never be over-committed by concurrent sends.
 */
public interface LimitReserver {

  ReservationOutcome reserve(NewSendMoneyTxn txn, LimitReservation reservation);
}
