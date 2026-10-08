package com.bracits.transactionservice.domain.txn;

import com.bracits.transactionservice.domain.Pricing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A transaction record to insert in DB transaction #1 (status INITIATED). {@code clientRef} is the Idempotency-Key;
 * {@code reference} may be null.
 */
public record NewSendMoneyTxn(
    UUID txnId,
    String clientRef,
    RequestHash requestHash,
    long senderWalletId,
    long receiverWalletId,
    long amount,
    Pricing pricing,
    String currency,
    String reference,
    LocalDate businessDate) {
}
