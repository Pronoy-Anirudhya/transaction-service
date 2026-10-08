package com.bracits.transactionservice.domain.ledger.planner;

import static com.bracits.transactionservice.domain.fixture.TestWallets.RECEIVER_ACCOUNT;
import static com.bracits.transactionservice.domain.fixture.TestWallets.SENDER_ACCOUNT;
import static com.bracits.transactionservice.domain.fixture.TestWallets.receiver;
import static com.bracits.transactionservice.domain.fixture.TestWallets.sender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.fee.util.FeeMath;
import com.bracits.transactionservice.domain.ledger.enums.LegCode;
import com.bracits.transactionservice.domain.ledger.model.Leg;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import com.bracits.transactionservice.domain.ledger.model.SystemAccounts;
import com.bracits.transactionservice.domain.ledger.planner.impl.LegPlannerImpl;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegPlannerTest {

  private static final UUID FEE_INCOME = UUID.fromString("00000000-0000-0000-0000-0000000000c8");
  private static final UUID VAT_PAYABLE = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
  private static final UUID COMMISSION_PAYABLE = UUID.fromString(
      "00000000-0000-0000-0000-0000000000dc");
  private static final UUID ISSUANCE = UUID.fromString("00000000-0000-0000-0000-000000000384");
  private static final UUID TXN_ID = UUID.fromString("0192f3a1-b2c3-4d5e-8f60-718293a4b500");

  private final LegPlanner planner =
      new LegPlannerImpl(new SystemAccounts(FEE_INCOME, VAT_PAYABLE, COMMISSION_PAYABLE, ISSUANCE));
  private final Wallet sender = sender();
  private final Wallet receiver = receiver();

  @Test
  void allPartsGiveFourLegsInSpecOrder() {
    Pricing pricing = new Pricing(500, 65, 87, 348);

    PostingRequest request = planner.plan(TXN_ID, sender, receiver, 100_000, pricing);

    assertThat(request.postingId()).isEqualTo(TXN_ID);
    assertThat(request.product()).isEqualTo(Product.SEND_MONEY);
    assertThat(request.userData64()).isEqualTo(sender.walletId());
    assertThat(request.legs()).containsExactly(
        new Leg(SENDER_ACCOUNT, RECEIVER_ACCOUNT, 100_000, LegCode.PRINCIPAL),
        new Leg(SENDER_ACCOUNT, FEE_INCOME, 348, LegCode.FEE),
        new Leg(SENDER_ACCOUNT, VAT_PAYABLE, 65, LegCode.VAT),
        new Leg(SENDER_ACCOUNT, COMMISSION_PAYABLE, 87, LegCode.COMMISSION));
    assertThat(LegPlanner.legCount(pricing)).isEqualTo(4);
  }

  @Test
  void legsDebitTheSenderForAmountPlusFee() {
    Pricing pricing = new Pricing(500, 65, 87, 348);

    long debited = planner.plan(TXN_ID, sender, receiver, 100_000, pricing).legs().stream()
        .mapToLong(Leg::amount).sum();

    assertThat(debited).isEqualTo(pricing.totalDebit(100_000));
  }

  @Test
  void zeroFeeGivesOnlyThePrincipal() {
    PostingRequest request = planner.plan(TXN_ID, sender, receiver, 5_000, Pricing.FREE);

    assertThat(request.legs()).containsExactly(
        new Leg(SENDER_ACCOUNT, RECEIVER_ACCOUNT, 5_000, LegCode.PRINCIPAL));
    assertThat(LegPlanner.legCount(Pricing.FREE)).isEqualTo(1);
  }

  @Test
  void zeroCommissionGivesThreeLegs() {
    Pricing pricing = FeeMath.split(500, 1_500, 0);

    PostingRequest request = planner.plan(TXN_ID, sender, receiver, 100_000, pricing);

    assertThat(request.legs()).extracting(Leg::code)
        .containsExactly(LegCode.PRINCIPAL, LegCode.FEE, LegCode.VAT);
    assertThat(request.legs()).extracting(Leg::amount).containsExactly(100_000L, 435L, 65L);
    assertThat(LegPlanner.legCount(pricing)).isEqualTo(3);
  }

  @Test
  void zeroVatGivesThreeLegs() {
    Pricing pricing = FeeMath.split(500, 0, 2_000);

    PostingRequest request = planner.plan(TXN_ID, sender, receiver, 100_000, pricing);

    assertThat(request.legs()).extracting(Leg::code)
        .containsExactly(LegCode.PRINCIPAL, LegCode.FEE, LegCode.COMMISSION);
    assertThat(LegPlanner.legCount(pricing)).isEqualTo(3);
  }

  @Test
  void zeroFeeIncomeIsOmittedToo() {
    // fee 1 poisha at 100% VAT: VAT = round half-up(0.5) = 1, nothing left for commission or income.
    Pricing pricing = FeeMath.split(1, 10_000, 2_000);

    PostingRequest request = planner.plan(TXN_ID, sender, receiver, 100_000, pricing);

    assertThat(request.legs()).extracting(Leg::code)
        .containsExactly(LegCode.PRINCIPAL, LegCode.VAT);
    assertThat(LegPlanner.legCount(pricing)).isEqualTo(2);
  }

  @Test
  void zeroAmountIsRejected() {
    assertThatThrownBy(() -> planner.plan(TXN_ID, sender, receiver, 0, Pricing.FREE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
