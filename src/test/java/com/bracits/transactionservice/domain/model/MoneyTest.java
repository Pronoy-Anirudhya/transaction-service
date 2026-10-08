package com.bracits.transactionservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void arithmetic() {
    assertThat(Money.of(100).plus(Money.of(50))).isEqualTo(Money.of(150));
    assertThat(Money.of(100).minus(Money.of(100))).isEqualTo(Money.ZERO);
  }

  @Test
  void zeroAndPositive() {
    assertThat(Money.ZERO.isZero()).isTrue();
    assertThat(Money.ZERO.isPositive()).isFalse();
    assertThat(Money.of(1).isZero()).isFalse();
    assertThat(Money.of(1).isPositive()).isTrue();
  }

  @Test
  void negativeIsRejected() {
    assertThatThrownBy(() -> Money.of(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.of(1).minus(Money.of(2))).isInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  void overflowIsDetected() {
    assertThatThrownBy(() -> Money.of(Long.MAX_VALUE).plus(Money.of(1))).isInstanceOf(
        ArithmeticException.class);
  }
}
