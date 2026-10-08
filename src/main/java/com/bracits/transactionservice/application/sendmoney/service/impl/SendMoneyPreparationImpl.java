package com.bracits.transactionservice.application.sendmoney.service.impl;

import com.bracits.transactionservice.application.result.PreparationResult;
import com.bracits.transactionservice.application.sendmoney.service.SendMoneyPreparation;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.fee.calculator.FeeCalculator;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.domain.limit.policy.LimitPolicy;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.rules.chain.SendMoneyRuleChain;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import com.bracits.transactionservice.port.out.repository.WalletRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link SendMoneyPreparation}.
 */
@Component
public final class SendMoneyPreparationImpl implements SendMoneyPreparation {

  private final WalletRepository wallets;
  private final LimitPolicy limitPolicy;
  private final SendMoneyRuleChain ruleChain;
  private final FeeCalculator feeCalculator;

  public SendMoneyPreparationImpl(
      WalletRepository wallets, LimitPolicy limitPolicy, SendMoneyRuleChain ruleChain,
      FeeCalculator feeCalculator) {
    this.wallets = wallets;
    this.limitPolicy = limitPolicy;
    this.ruleChain = ruleChain;
    this.feeCalculator = feeCalculator;
  }

  @Override
  public PreparationResult prepare(String senderMsisdn, String receiverMsisdn, long amount) {
    Optional<Wallet> sender = wallets.findByMsisdn(senderMsisdn);
    Optional<Wallet> receiver = wallets.findByMsisdn(receiverMsisdn);
    Optional<LimitRule> limitRule = sender.flatMap(
        w -> limitPolicy.ruleFor(Product.SEND_MONEY, w.kycTier()));

    Optional<FailureCode> failure = ruleChain.evaluate(
        new SendMoneyContext(senderMsisdn, receiverMsisdn, sender, receiver, amount, limitRule));
    if (failure.isPresent()) {
      return new PreparationResult.Failed(failure.get(), sender);
    }

    Wallet from = sender.orElseThrow();
    Optional<Pricing> pricing = feeCalculator.price(Product.SEND_MONEY, from.kycTier(), amount);
    if (pricing.isEmpty()) {
      return new PreparationResult.Failed(FailureCode.AMOUNT_OUT_OF_RANGE, sender);
    }

    return new PreparationResult.Ready(from, receiver.orElseThrow(), limitRule.orElseThrow(),
        pricing.get());
  }
}
