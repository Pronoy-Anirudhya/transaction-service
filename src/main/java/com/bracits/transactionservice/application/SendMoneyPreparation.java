package com.bracits.transactionservice.application;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.Product;
import com.bracits.transactionservice.domain.fee.FeeCalculator;
import com.bracits.transactionservice.domain.limit.LimitPolicy;
import com.bracits.transactionservice.domain.limit.LimitRule;
import com.bracits.transactionservice.domain.rules.SendMoneyContext;
import com.bracits.transactionservice.domain.rules.SendMoneyRuleChain;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.port.out.WalletRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spec 5 steps 2–4, all in memory: load both wallets from the cache, run the rule chain (BR-01, BR-02) and price
 * (BR-04..BR-07). Shared by the quote and Send Money use cases.
 */
@Component
public final class SendMoneyPreparation {

  private final WalletRepository wallets;
  private final LimitPolicy limitPolicy;
  private final SendMoneyRuleChain ruleChain;
  private final FeeCalculator feeCalculator;

  public SendMoneyPreparation(
      WalletRepository wallets, LimitPolicy limitPolicy, SendMoneyRuleChain ruleChain, FeeCalculator feeCalculator) {
    this.wallets = wallets;
    this.limitPolicy = limitPolicy;
    this.ruleChain = ruleChain;
    this.feeCalculator = feeCalculator;
  }

  public Prepared prepare(String senderMsisdn, String receiverMsisdn, long amount) {
    Optional<Wallet> sender = wallets.findByMsisdn(senderMsisdn);
    Optional<Wallet> receiver = wallets.findByMsisdn(receiverMsisdn);
    Optional<LimitRule> limitRule = sender.flatMap(w -> limitPolicy.ruleFor(Product.SEND_MONEY, w.kycTier()));
    Optional<FailureCode> failure = ruleChain.evaluate(
        new SendMoneyContext(senderMsisdn, receiverMsisdn, sender, receiver, amount, limitRule));
    if (failure.isPresent()) {
      return new Prepared.Failed(failure.get(), sender);
    }
    Wallet from = sender.orElseThrow();
    Optional<Pricing> pricing = feeCalculator.price(Product.SEND_MONEY, from.kycTier(), amount);
    if (pricing.isEmpty()) {
      return new Prepared.Failed(FailureCode.AMOUNT_OUT_OF_RANGE, sender);
    }
    return new Prepared.Ready(from, receiver.orElseThrow(), limitRule.orElseThrow(), pricing.get());
  }

  /** Result of the in-memory preparation. */
  public sealed interface Prepared {

    /** Every rule passed and the transaction is priced. */
    record Ready(Wallet sender, Wallet receiver, LimitRule limitRule, Pricing pricing) implements Prepared {
    }

    /** A rule failed; {@code sender} is kept for the idempotent-replay look-up. */
    record Failed(FailureCode code, Optional<Wallet> sender) implements Prepared {
    }
  }
}
