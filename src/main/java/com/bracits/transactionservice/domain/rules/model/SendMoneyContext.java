package com.bracits.transactionservice.domain.rules.model;

import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the in-memory rule chain needs (BR-01, BR-02). Wallets are empty when no wallet has
 * that MSISDN; {@code senderLimitRule} is empty when the sender is unknown or its tier has no limit
 * rule.
 */
public record SendMoneyContext(
    String senderMsisdn,
    String receiverMsisdn,
    Optional<Wallet> sender,
    Optional<Wallet> receiver,
    long amount,
    Optional<LimitRule> senderLimitRule) {

  public SendMoneyContext {
    Objects.requireNonNull(senderMsisdn);
    Objects.requireNonNull(receiverMsisdn);
    Objects.requireNonNull(sender);
    Objects.requireNonNull(receiver);
    Objects.requireNonNull(senderLimitRule);
  }
}
