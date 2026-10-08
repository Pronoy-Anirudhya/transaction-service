package com.bracits.transactionservice.domain.fee.calculator.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.fee.enums.FeeType;
import com.bracits.transactionservice.domain.fee.model.FeeRule;
import com.bracits.transactionservice.domain.model.Pricing;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SlabFeeCalculatorTest {

  private static final int TIER = 1;

  /**
   * Seed data of spec 3: fee 0 for 1–100 BDT; 5 BDT flat for 100.01–25,000 BDT.
   */
  private static final FeeRule FREE_SLAB = flat(1, 100, 10_000, 0);
  private static final FeeRule FLAT_SLAB = flat(2, 10_001, 2_500_000, 500);

  private final SlabFeeCalculator calculator = new SlabFeeCalculator(
      () -> List.of(FREE_SLAB, FLAT_SLAB));

  @Test
  void workedExample() {
    assertThat(calculator.price(Product.SEND_MONEY, TIER, 100_000)).contains(
        new Pricing(500, 65, 87, 348));
  }

  @Test
  void slabBoundariesAreInclusive() {
    assertThat(calculator.price(Product.SEND_MONEY, TIER, 100)).contains(Pricing.FREE);
    assertThat(calculator.price(Product.SEND_MONEY, TIER, 10_000)).contains(Pricing.FREE);
    assertThat(calculator.price(Product.SEND_MONEY, TIER, 10_001)).map(Pricing::fee).contains(500L);
    assertThat(calculator.price(Product.SEND_MONEY, TIER, 2_500_000)).map(Pricing::fee)
        .contains(500L);
  }

  @Test
  void noMatchingSlabIsEmpty() {
    assertThat(calculator.price(Product.SEND_MONEY, TIER, 99)).isEmpty();
    assertThat(calculator.price(Product.SEND_MONEY, TIER, 2_500_001)).isEmpty();
    assertThat(new SlabFeeCalculator(List::of).price(Product.SEND_MONEY, TIER, 100_000)).isEmpty();
  }

  @Test
  void otherTierIsIgnored() {
    assertThat(calculator.price(Product.SEND_MONEY, 2, 100_000)).isEmpty();
  }

  @Test
  void ruleOfOtherTierIsSkippedEvenWhenListedFirst() {
    // Product has a single value (SEND_MONEY) today, so the product filter is covered by FeeRule.covers only.
    FeeRule otherTier = new FeeRule(3, Product.SEND_MONEY, 9, 1, 2_500_000, FeeType.FLAT, 999, 0,
        OptionalLong.empty(), 1_500, 2_000, true);
    SlabFeeCalculator calc = new SlabFeeCalculator(() -> List.of(otherTier, FLAT_SLAB));

    assertThat(calc.price(Product.SEND_MONEY, TIER, 100_000)).map(Pricing::fee).contains(500L);
    assertThat(calc.price(Product.SEND_MONEY, 9, 100_000)).map(Pricing::fee).contains(999L);
  }

  @Test
  void inactiveRuleIsIgnored() {
    FeeRule inactive = new FeeRule(4, Product.SEND_MONEY, TIER, 1, 2_500_000, FeeType.FLAT, 999, 0,
        OptionalLong.empty(), 1_500, 2_000, false);
    SlabFeeCalculator calc = new SlabFeeCalculator(() -> List.of(inactive, FLAT_SLAB));

    assertThat(calc.price(Product.SEND_MONEY, TIER, 100_000)).map(Pricing::fee).contains(500L);
    assertThat(new SlabFeeCalculator(() -> List.of(inactive)).price(Product.SEND_MONEY, TIER,
        100_000)).isEmpty();
  }

  @Test
  void firstMatchingRuleWins() {
    FeeRule overlapping = flat(5, 1, 2_500_000, 700);
    SlabFeeCalculator calc = new SlabFeeCalculator(() -> List.of(overlapping, FLAT_SLAB));

    assertThat(calc.price(Product.SEND_MONEY, TIER, 100_000)).map(Pricing::fee).contains(700L);
  }

  @Test
  void percentFeeIsClampedToMin() {
    // 1% of 1,000 poisha = 10, clamped up to 50.
    FeeRule rule = percent(100, 50, OptionalLong.of(2_000));

    assertThat(price(rule, 1_000)).isEqualTo(50);
  }

  @Test
  void percentFeeIsClampedToMax() {
    // 1% of 1,000,000 poisha = 10,000, clamped down to 2,000.
    FeeRule rule = percent(100, 50, OptionalLong.of(2_000));

    assertThat(price(rule, 1_000_000)).isEqualTo(2_000);
  }

  @Test
  void percentFeeWithinClampIsUnchanged() {
    FeeRule rule = percent(100, 50, OptionalLong.of(2_000));

    assertThat(price(rule, 100_000)).isEqualTo(1_000);
  }

  @Test
  void emptyFeeMaxMeansNoUpperClamp() {
    FeeRule rule = percent(100, 50, OptionalLong.empty());

    assertThat(price(rule, 1_000_000_000)).isEqualTo(10_000_000);
  }

  @Test
  void percentFeeRoundsHalfUp() {
    // 185 bps of 2,000 = 37.0; of 2,030 = 37.555 -> 38; of 2,027 = 37.4995 -> 37.
    FeeRule rule = percent(185, 0, OptionalLong.empty());

    assertThat(price(rule, 2_000)).isEqualTo(37);
    assertThat(price(rule, 2_030)).isEqualTo(38);
    assertThat(price(rule, 2_027)).isEqualTo(37);
  }

  @Test
  void rulesAreReadOnEveryCall() {
    AtomicInteger reads = new AtomicInteger();
    SlabFeeCalculator calc = new SlabFeeCalculator(() -> {
      reads.incrementAndGet();
      return List.of(FLAT_SLAB);
    });

    calc.price(Product.SEND_MONEY, TIER, 100_000);
    calc.price(Product.SEND_MONEY, TIER, 100_000);

    assertThat(reads).hasValue(2);
  }

  private static long price(FeeRule rule, long amount) {
    return new SlabFeeCalculator(() -> List.of(rule)).price(Product.SEND_MONEY, TIER, amount)
        .orElseThrow().fee();
  }

  private static FeeRule flat(long id, long min, long max, long fee) {
    return new FeeRule(id, Product.SEND_MONEY, TIER, min, max, FeeType.FLAT, fee, 0,
        OptionalLong.empty(),
        1_500, 2_000, true);
  }

  private static FeeRule percent(long bps, long feeMin, OptionalLong feeMax) {
    return new FeeRule(9, Product.SEND_MONEY, TIER, 1, Long.MAX_VALUE / 10_000, FeeType.PERCENT,
        bps, feeMin, feeMax,
        1_500, 2_000, true);
  }
}
