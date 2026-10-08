package com.bracits.transactionservice.domain.rules.rule.impl;

import static com.bracits.transactionservice.domain.fixture.TestContexts.TIER_1;
import static com.bracits.transactionservice.domain.fixture.TestContexts.ctx;
import static com.bracits.transactionservice.domain.fixture.TestContexts.valid;
import static com.bracits.transactionservice.domain.fixture.TestWallets.RECEIVER_MSISDN;
import static com.bracits.transactionservice.domain.fixture.TestWallets.SENDER_MSISDN;
import static com.bracits.transactionservice.domain.fixture.TestWallets.receiver;
import static com.bracits.transactionservice.domain.fixture.TestWallets.sender;
import static com.bracits.transactionservice.domain.fixture.TestWallets.withStatus;
import static com.bracits.transactionservice.domain.fixture.TestWallets.withType;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import com.bracits.transactionservice.domain.wallet.enums.WalletStatus;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SendMoneyRulesTest {

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
      assertThat(rule.check(
          ctx(Optional.of(withStatus(sender(), status)), Optional.of(receiver()), 100_000)))
          .contains(FailureCode.WALLET_INACTIVE);
    }

    @ParameterizedTest
    @EnumSource(value = WalletStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void inactiveReceiverFails(WalletStatus status) {
      assertThat(rule.check(
          ctx(Optional.of(sender()), Optional.of(withStatus(receiver(), status)), 100_000)))
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
      assertThat(rule.check(
          ctx(Optional.of(withType(sender(), WalletType.SYSTEM)), Optional.of(receiver()), 1)))
          .contains(FailureCode.WALLET_NOT_FOUND);
    }

    @Test
    void nonCustomerReceiverIsNotAllowed() {
      assertThat(rule.check(
          ctx(Optional.of(sender()), Optional.of(withType(receiver(), WalletType.SYSTEM)), 1)))
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
      assertThat(
          rule.check(ctx(Optional.of(sender()), Optional.of(receiver()), 2_500_000))).isEmpty();
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
      SendMoneyContext c = new SendMoneyContext(SENDER_MSISDN, RECEIVER_MSISDN,
          Optional.of(sender()),
          Optional.of(receiver()), 100_000, Optional.empty());

      assertThat(rule.check(c)).contains(FailureCode.AMOUNT_OUT_OF_RANGE);
    }
  }
}
