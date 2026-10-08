package com.bracits.transactionservice.domain.limit.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.domain.limit.policy.impl.LimitPolicyImpl;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LimitPolicyTest {

  private static final LimitRule TIER_1 = new LimitRule(Product.SEND_MONEY, 1, 1_000, 2_500_000,
      5_000_000, 50,
      30_000_000, 200);
  private static final LimitRule TIER_2 = new LimitRule(Product.SEND_MONEY, 2, 1_000, 5_000_000,
      10_000_000, 100,
      60_000_000, 400);

  @Test
  void findsRuleByProductAndTier() {
    LimitPolicy policy = new LimitPolicyImpl(() -> List.of(TIER_1, TIER_2));

    assertThat(policy.ruleFor(Product.SEND_MONEY, 1)).contains(TIER_1);
    assertThat(policy.ruleFor(Product.SEND_MONEY, 2)).contains(TIER_2);
    assertThat(policy.ruleFor(Product.SEND_MONEY, 3)).isEmpty();
  }

  @Test
  void readsRulesOnEveryCall() {
    AtomicReference<List<LimitRule>> rules = new AtomicReference<>(List.of());
    LimitPolicy policy = new LimitPolicyImpl(rules::get);

    assertThat(policy.ruleFor(Product.SEND_MONEY, 1)).isEmpty();

    rules.set(List.of(TIER_1));

    assertThat(policy.ruleFor(Product.SEND_MONEY, 1)).contains(TIER_1);
  }

  @Test
  void perTransactionRangeIsInclusive() {
    assertThat(TIER_1.allowsAmount(999)).isFalse();
    assertThat(TIER_1.allowsAmount(1_000)).isTrue();
    assertThat(TIER_1.allowsAmount(2_500_000)).isTrue();
    assertThat(TIER_1.allowsAmount(2_500_001)).isFalse();
  }

  @Test
  void reservationMonthIsFirstDayOfBusinessMonth() {
    assertThat(new LimitReservation(1, LocalDate.of(2026, 10, 8), 100, TIER_1).month())
        .isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(new LimitReservation(1, LocalDate.of(2024, 2, 29), 100, TIER_1).month())
        .isEqualTo(LocalDate.of(2024, 2, 1));
    assertThat(new LimitReservation(1, LocalDate.of(2026, 1, 1), 100, TIER_1).month())
        .isEqualTo(LocalDate.of(2026, 1, 1));
  }
}
