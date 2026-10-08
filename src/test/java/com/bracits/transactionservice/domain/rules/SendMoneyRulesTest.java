package com.bracits.transactionservice.domain.rules;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Product;
import com.bracits.transactionservice.domain.limit.LimitRule;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import com.bracits.transactionservice.domain.wallet.WalletType;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static com.bracits.transactionservice.domain.TestWallets.RECEIVER_MSISDN;
import static com.bracits.transactionservice.domain.TestWallets.SENDER_MSISDN;
import static com.bracits.transactionservice.domain.TestWallets.receiver;
import static com.bracits.transactionservice.domain.TestWallets.sender;
import static com.bracits.transactionservice.domain.TestWallets.withStatus;
import static com.bracits.transactionservice.domain.TestWallets.withType;
import static org.assertj.core.api.Assertions.assertThat;

class SendMoneyRulesTest {

  static final LimitRule TIER_1 = new LimitRule(Product.SEND_MONEY, 1, 1_000, 2_500_000, 5_000_000, 50,
      30_000_000, 200);

  static SendMoneyContext ctx(Optional<Wallet> sender, Optional<Wallet> receiver, long amount) {
    return new SendMoneyContext(SENDER_MSISDN, RECEIVER_MSISDN, sender, receiver, amount, Optional.of(TIER_1));
  }

  static SendMoneyContext valid() {
    return ctx(Optional.of(sender()), Optional.of(receiver()), 100_000);
  }

  @Nested
  class SelfTransfer {

    private final SelfTransferRule rule = new SelfTransferRule();

    @Test
    void sameMsisdnFails() {
      SendMoneyContext c = new SendMoneyContext(SENDER_MSISDN, SENDER_MSISDN, Optional.of(sender()),
          Optional.of(sender()), 100_000, Optional.of(TIER_1));
      assertThat(rule.check(c)).contains(FailureCode.SELF_TRANSFER);
    }

    @Test
    void differentMsisdnPasses() {
      assertThat(rule.check(valid())).isEmpty();
    }
  }

  @Nested
  class WalletExists {

    private final WalletExistsRule rule = new WalletExistsRule();

    @Test
    void missingSenderFails() {
      assertThat(rule.check(ctx(Optional.empty(), Optional.of(receiver()), 100_000)))
          .contains(FailureCode.WALLET_NOT_FOUND);
    }

    @Test
    void missingReceiverFails() {
      assertThat(rule.check(ctx(Optional.of(sender()), Optional.empty(), 100_000)))
          .contains(FailureCode.WALLET_NOT_FOUND);
    }

    @Test
    void bothPresentPasses() {
      assertThat(rule.check(valid())).isEmpty();
    }
  }

  @Nested
  class WalletActive {

    private final WalletActiveRule rule = new WalletActiveRule();

    @ParameterizedTest
    @EnumSource(value = WalletStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void inactiveSenderFails(WalletStatus status) {
      assertThat(rule.check(ctx(Optional.of(withStatus(sender(), status)), Optional.of(receiver()), 100_000)))
          .contains(FailureCode.WALLET_INACTIVE);
    }

    @ParameterizedTest
    @EnumSource(value = WalletStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void inactiveReceiverFails(WalletStatus status) {
      assertThat(rule.check(ctx(Optional.of(sender()), Optional.of(withStatus(receiver(), status)), 100_000)))
          .contains(FailureCode.WALLET_INACTIVE);
    }

    @Test
    void bothActivePasses() {
      assertThat(rule.check(valid())).isEmpty();
    }

    @Test
    void missingWalletsDoNotThrow() {
      assertThat(rule.check(ctx(Optional.empty(), Optional.empty(), 100_000))).isEmpty();
    }
  }

  @Nested
  class CustomerType {

    private final CustomerTypeRule rule = new CustomerTypeRule();

    @Test
    void nonCustomerSenderIsReportedAsNotFound() {
      assertThat(rule.check(ctx(Optional.of(withType(sender(), WalletType.SYSTEM)), Optional.of(receiver()), 1)))
          .contains(FailureCode.WALLET_NOT_FOUND);
    }

    @Test
    void nonCustomerReceiverIsNotAllowed() {
      assertThat(rule.check(ctx(Optional.of(sender()), Optional.of(withType(receiver(), WalletType.SYSTEM)), 1)))
          .contains(FailureCode.RECEIVER_NOT_ALLOWED);
    }

    @Test
    void senderIsCheckedFirst() {
      assertThat(rule.check(ctx(Optional.of(withType(sender(), WalletType.SYSTEM)),
          Optional.of(withType(receiver(), WalletType.SYSTEM)), 1)))
          .contains(FailureCode.WALLET_NOT_FOUND);
    }

    @Test
    void customersPass() {
      assertThat(rule.check(valid())).isEmpty();
    }

    @Test
    void missingWalletsDoNotThrow() {
      assertThat(rule.check(ctx(Optional.empty(), Optional.empty(), 100_000))).isEmpty();
    }
  }

  @Nested
  class AmountRange {

    private final AmountRangeRule rule = new AmountRangeRule();

    @Test
    void boundariesAreInclusive() {
      assertThat(rule.check(ctx(Optional.of(sender()), Optional.of(receiver()), 1_000))).isEmpty();
      assertThat(rule.check(ctx(Optional.of(sender()), Optional.of(receiver()), 2_500_000))).isEmpty();
    }

    @Test
    void belowMinimumFails() {
      assertThat(rule.check(ctx(Optional.of(sender()), Optional.of(receiver()), 999)))
          .contains(FailureCode.AMOUNT_OUT_OF_RANGE);
    }

    @Test
    void aboveMaximumFails() {
      assertThat(rule.check(ctx(Optional.of(sender()), Optional.of(receiver()), 2_500_001)))
          .contains(FailureCode.AMOUNT_OUT_OF_RANGE);
    }

    @Test
    void missingLimitRuleFails() {
      SendMoneyContext c = new SendMoneyContext(SENDER_MSISDN, RECEIVER_MSISDN, Optional.of(sender()),
          Optional.of(receiver()), 100_000, Optional.empty());
      assertThat(rule.check(c)).contains(FailureCode.AMOUNT_OUT_OF_RANGE);
    }
  }
}
