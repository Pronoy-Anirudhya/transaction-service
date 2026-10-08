package com.bracits.transactionservice.domain.rules;

import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import com.bracits.transactionservice.domain.wallet.WalletType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.bracits.transactionservice.domain.TestWallets.RECEIVER_MSISDN;
import static com.bracits.transactionservice.domain.TestWallets.SENDER_MSISDN;
import static com.bracits.transactionservice.domain.TestWallets.receiver;
import static com.bracits.transactionservice.domain.TestWallets.sender;
import static com.bracits.transactionservice.domain.TestWallets.withStatus;
import static com.bracits.transactionservice.domain.TestWallets.withType;
import static com.bracits.transactionservice.domain.rules.SendMoneyRulesTest.ctx;
import static com.bracits.transactionservice.domain.rules.SendMoneyRulesTest.valid;
import static org.assertj.core.api.Assertions.assertThat;

class SendMoneyRuleChainTest {

  private final SendMoneyRuleChain chain = SendMoneyRuleChain.standard();

  @Test
  void standardOrder() {
    assertThat(chain.rules()).extracting(Object::getClass).containsExactly(
        SelfTransferRule.class,
        WalletExistsRule.class,
        WalletActiveRule.class,
        CustomerTypeRule.class,
        AmountRangeRule.class);
  }

  @Test
  void validContextPasses() {
    assertThat(chain.evaluate(valid())).isEmpty();
  }

  @Test
  void selfTransferWithMissingWalletsIsSelfTransfer() {
    SendMoneyContext c = new SendMoneyContext(SENDER_MSISDN, SENDER_MSISDN, Optional.empty(), Optional.empty(),
        1, Optional.empty());
    assertThat(chain.evaluate(c)).contains(FailureCode.SELF_TRANSFER);
  }

  @Test
  void missingWalletWinsOverAmountOutOfRange() {
    SendMoneyContext c = new SendMoneyContext(SENDER_MSISDN, RECEIVER_MSISDN, Optional.empty(),
        Optional.of(receiver()), 1, Optional.empty());
    assertThat(chain.evaluate(c)).contains(FailureCode.WALLET_NOT_FOUND);
  }

  @Test
  void inactiveWinsOverWrongType() {
    SendMoneyContext c = ctx(Optional.of(withType(withStatus(sender(), WalletStatus.FROZEN), WalletType.SYSTEM)),
        Optional.of(receiver()), 100_000);
    assertThat(chain.evaluate(c)).contains(FailureCode.WALLET_INACTIVE);
  }

  @Test
  void wrongTypeWinsOverAmountOutOfRange() {
    SendMoneyContext c = ctx(Optional.of(sender()), Optional.of(withType(receiver(), WalletType.SYSTEM)), 1);
    assertThat(chain.evaluate(c)).contains(FailureCode.RECEIVER_NOT_ALLOWED);
  }

  @Test
  void amountOutOfRangeIsLast() {
    assertThat(chain.evaluate(ctx(Optional.of(sender()), Optional.of(receiver()), 1)))
        .contains(FailureCode.AMOUNT_OUT_OF_RANGE);
  }

  @Test
  void stopsAtFirstFailure() {
    List<String> called = new ArrayList<>();
    SendMoneyRuleChain custom = new SendMoneyRuleChain(List.of(
        c -> {
          called.add("first");
          return Optional.empty();
        },
        c -> {
          called.add("second");
          return Optional.of(FailureCode.LIMIT_EXCEEDED);
        },
        c -> {
          called.add("third");
          return Optional.of(FailureCode.SELF_TRANSFER);
        }));

    assertThat(custom.evaluate(valid())).contains(FailureCode.LIMIT_EXCEEDED);
    assertThat(called).containsExactly("first", "second");
  }

  @Test
  void emptyChainPasses() {
    assertThat(new SendMoneyRuleChain(List.of()).evaluate(valid())).isEmpty();
  }
}
