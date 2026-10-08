package com.bracits.transactionservice.application.sendmoney.service;

import static com.bracits.transactionservice.application.fakes.Fixtures.AMOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.RECEIVER_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.STANDARD_PRICING;
import static com.bracits.transactionservice.application.fakes.Fixtures.UNKNOWN_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.receiver;
import static com.bracits.transactionservice.application.fakes.Fixtures.sender;
import static com.bracits.transactionservice.application.fakes.Fixtures.withTier;
import static com.bracits.transactionservice.application.fakes.Fixtures.withType;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.application.fakes.InMemoryRuleRepository;
import com.bracits.transactionservice.application.fakes.SendMoneyHarness;
import com.bracits.transactionservice.application.result.PreparationResult;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SendMoneyPreparationTest {

  private final SendMoneyHarness h = new SendMoneyHarness();

  @Test
  void readyCarriesBothWalletsTheLimitRuleAndThePricing() {
    assertThat(h.preparation.prepare(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT)).isEqualTo(
        new PreparationResult.Ready(sender(), receiver(), InMemoryRuleRepository.TIER_1_LIMIT,
            STANDARD_PRICING));
  }

  @Test
  void failureKeepsTheSenderForTheReplayLookUp() {
    assertThat(h.preparation.prepare(SENDER_MSISDN, UNKNOWN_MSISDN, AMOUNT))
        .isEqualTo(
            new PreparationResult.Failed(FailureCode.WALLET_NOT_FOUND, Optional.of(sender())));
    assertThat(h.preparation.prepare(UNKNOWN_MSISDN, RECEIVER_MSISDN, AMOUNT))
        .isEqualTo(new PreparationResult.Failed(FailureCode.WALLET_NOT_FOUND, Optional.empty()));
  }

  @Test
  void nonCustomerSenderIsReportedAsNotFound() {
    h.wallets.put(withType(sender(), WalletType.SYSTEM));

    assertThat(h.preparation.prepare(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT))
        .isInstanceOfSatisfying(PreparationResult.Failed.class,
            f -> assertThat(f.code()).isEqualTo(FailureCode.WALLET_NOT_FOUND));
  }

  @Test
  void tierWithoutALimitRuleIsAmountOutOfRange() {
    h.wallets.put(withTier(sender(), 3));

    assertThat(h.preparation.prepare(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT))
        .isInstanceOfSatisfying(PreparationResult.Failed.class,
            f -> assertThat(f.code()).isEqualTo(FailureCode.AMOUNT_OUT_OF_RANGE));
  }

  @Test
  void noFeeSlabIsAmountOutOfRangeWithTheSenderKept() {
    h.rules.setFeeRules(List.of());

    assertThat(h.preparation.prepare(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT))
        .isEqualTo(
            new PreparationResult.Failed(FailureCode.AMOUNT_OUT_OF_RANGE, Optional.of(sender())));
  }

  @Test
  void slabBoundariesAreInclusive() {
    assertThat(h.preparation.prepare(SENDER_MSISDN, RECEIVER_MSISDN, 10_000L))
        .isInstanceOfSatisfying(PreparationResult.Ready.class,
            r -> assertThat(r.pricing().fee()).isZero());
    assertThat(h.preparation.prepare(SENDER_MSISDN, RECEIVER_MSISDN, 10_001L))
        .isInstanceOfSatisfying(PreparationResult.Ready.class,
            r -> assertThat(r.pricing().fee()).isEqualTo(500L));
    assertThat(h.preparation.prepare(SENDER_MSISDN, RECEIVER_MSISDN, 1_000L)).isInstanceOf(
        PreparationResult.Ready.class);
    assertThat(h.preparation.prepare(SENDER_MSISDN, RECEIVER_MSISDN, 2_500_000L)).isInstanceOf(
        PreparationResult.Ready.class);
  }
}
