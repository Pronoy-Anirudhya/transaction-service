package com.bracits.transactionservice.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingTest {

  @Test
  void validPricing() {
    Pricing pricing = new Pricing(500, 65, 87, 348);

    assertThat(pricing.totalDebit(100_000)).isEqualTo(100_500);
  }

  @Test
  void freeIsAllZero() {
    assertThat(Pricing.FREE).isEqualTo(new Pricing(0, 0, 0, 0));
    assertThat(Pricing.FREE.totalDebit(100)).isEqualTo(100);
  }

  @Test
  void partsMustSumToFee() {
    assertThatThrownBy(() -> new Pricing(500, 65, 87, 347)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Pricing(500, 65, 87, 349)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void partsMustNotBeNegative() {
    assertThatThrownBy(() -> new Pricing(-1, 0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Pricing(500, -1, 87, 414)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Pricing(500, 65, -1, 436)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Pricing(500, 600, 0, -100)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void totalDebitOverflowIsDetected() {
    assertThatThrownBy(() -> new Pricing(1, 0, 0, 1).totalDebit(Long.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class);
  }
}
