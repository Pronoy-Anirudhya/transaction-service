package com.bracits.transactionservice.application.sendmoney.service;

import com.bracits.transactionservice.application.result.PreparationResult;

/**
 * Spec 5 steps 2–4, all in memory: load both wallets from the cache, run the rule chain (BR-01,
 * BR-02) and price (BR-04..BR-07). Shared by the quote and Send Money use cases.
 */
public interface SendMoneyPreparation {

  PreparationResult prepare(String senderMsisdn, String receiverMsisdn, long amount);
}
