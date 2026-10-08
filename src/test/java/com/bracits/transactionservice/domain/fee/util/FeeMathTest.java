package com.bracits.transactionservice.domain.fee.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.transactionservice.domain.model.Pricing;
import org.junit.jupiter.api.Test;

class FeeMathTest {

  @Test
  void workedExampleFromSpec() {
    Pricing pricing = FeeMath.split(500, 1_500, 2_000);

    assertThat(pricing).isEqualTo(new Pricing(500, 65, 87, 348));
    assertThat(pricing.totalDebit(100_000)).isEqualTo(100_500);
  }

  @Test
  void percentRoundsHalfUp() {
    assertThat(FeeMath.percentHalfUp(1, 5_000)).isEqualTo(1);   // 0.5 -> 1
    assertThat(FeeMath.percentHalfUp(1, 4_999)).isZero();       // 0.4999 -> 0
    assertThat(FeeMath.percentHalfUp(3, 5_000)).isEqualTo(2);   // 1.5 -> 2
    assertThat(FeeMath.percentHalfUp(100_000, 185)).isEqualTo(1_850);
    assertThat(FeeMath.percentHalfUp(0, 185)).isZero();
  }

  @Test
  void vatRoundsHalfUp() {
    assertThat(FeeMath.vatHalfUp(500, 1_500)).isEqualTo(65);  // 65.21...
    assertThat(FeeMath.vatHalfUp(1, 10_000)).isEqualTo(1);    // exactly 0.5 -> 1
    assertThat(FeeMath.vatHalfUp(23, 1_500)).isEqualTo(3);    // 3.0 exactly
    assertThat(FeeMath.vatHalfUp(500, 0)).isZero();
  }

  @Test
  void commissionRoundsDown() {
    assertThat(FeeMath.commissionFloor(435, 2_000)).isEqualTo(87);
    assertThat(FeeMath.commissionFloor(9, 1_999)).isEqualTo(1);   // 1.799 -> 1
    assertThat(FeeMath.commissionFloor(435, 10_000)).isEqualTo(435);
    assertThat(FeeMath.commissionFloor(435, 0)).isZero();
  }

  @Test
  void zeroFeeIsFree() {
    assertThat(FeeMath.split(0, 1_500, 2_000)).isEqualTo(Pricing.FREE);
  }

  @Test
  void rejectsInvalidInputs() {
    assertThatThrownBy(() -> FeeMath.split(-1, 1_500, 2_000)).isInstanceOf(
        IllegalArgumentException.class);
    assertThatThrownBy(() -> FeeMath.split(500, -1, 2_000)).isInstanceOf(
        IllegalArgumentException.class);
    assertThatThrownBy(() -> FeeMath.split(500, 1_500, -1)).isInstanceOf(
        IllegalArgumentException.class);
    assertThatThrownBy(() -> FeeMath.split(500, 1_500, 10_001)).isInstanceOf(
        IllegalArgumentException.class);

    assertThatThrownBy(() -> FeeMath.percentHalfUp(-1, 100)).isInstanceOf(
        IllegalArgumentException.class);
    assertThatThrownBy(() -> FeeMath.percentHalfUp(1, -100)).isInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  void overflowIsDetected() {
    assertThatThrownBy(() -> FeeMath.percentHalfUp(Long.MAX_VALUE, 2)).isInstanceOf(
        ArithmeticException.class);
    assertThatThrownBy(() -> FeeMath.vatHalfUp(Long.MAX_VALUE, 2)).isInstanceOf(
        ArithmeticException.class);
  }
}
