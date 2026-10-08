package com.bracits.transactionservice.domain.fixture;

import static com.bracits.transactionservice.domain.fixture.TestWallets.RECEIVER_MSISDN;
import static com.bracits.transactionservice.domain.fixture.TestWallets.SENDER_MSISDN;
import static com.bracits.transactionservice.domain.fixture.TestWallets.receiver;
import static com.bracits.transactionservice.domain.fixture.TestWallets.sender;

import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.Optional;

/**
 * Rule-chain contexts shared by the rule and chain tests: the tier-1 limit rule of spec 3 and a
 * context builder.
 */
public final class TestContexts {

  public static final LimitRule TIER_1 = new LimitRule(Product.SEND_MONEY, 1, 1_000, 2_500_000,
      5_000_000, 50, 30_000_000, 200);

  private TestContexts() {
  }

  public static SendMoneyContext ctx(Optional<Wallet> sender, Optional<Wallet> receiver,
      long amount) {
    return new SendMoneyContext(SENDER_MSISDN, RECEIVER_MSISDN, sender, receiver, amount,
        Optional.of(TIER_1));
  }

  public static SendMoneyContext valid() {
    return ctx(Optional.of(sender()), Optional.of(receiver()), 100_000);
  }
}
