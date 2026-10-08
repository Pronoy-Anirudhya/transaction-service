package com.bracits.transactionservice.domain.rules.chain;

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
import com.bracits.transactionservice.domain.rules.chain.impl.SendMoneyRuleChainImpl;
import com.bracits.transactionservice.domain.rules.model.SendMoneyContext;
import com.bracits.transactionservice.domain.rules.rule.impl.AmountRangeRule;
import com.bracits.transactionservice.domain.rules.rule.impl.CustomerTypeRule;
import com.bracits.transactionservice.domain.rules.rule.impl.SelfTransferRule;
import com.bracits.transactionservice.domain.rules.rule.impl.WalletActiveRule;
import com.bracits.transactionservice.domain.rules.rule.impl.WalletExistsRule;
import com.bracits.transactionservice.domain.wallet.enums.WalletStatus;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SendMoneyRuleChainTest {

  private final SendMoneyRuleChain chain = SendMoneyRuleChainImpl.standard();

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
    SendMoneyContext c = new SendMoneyContext(SENDER_MSISDN, SENDER_MSISDN, Optional.empty(),
        Optional.empty(),
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
    SendMoneyContext c = ctx(
        Optional.of(withType(withStatus(sender(), WalletStatus.FROZEN), WalletType.SYSTEM)),
        Optional.of(receiver()), 100_000);

    assertThat(chain.evaluate(c)).contains(FailureCode.WALLET_INACTIVE);
  }

  @Test
  void wrongTypeWinsOverAmountOutOfRange() {
    SendMoneyContext c = ctx(Optional.of(sender()),
        Optional.of(withType(receiver(), WalletType.SYSTEM)), 1);

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
    SendMoneyRuleChain custom = new SendMoneyRuleChainImpl(List.of(
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
    assertThat(new SendMoneyRuleChainImpl(List.of()).evaluate(valid())).isEmpty();
  }
}
