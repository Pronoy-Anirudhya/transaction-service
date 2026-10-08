package com.bracits.transactionservice.domain.wallet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MsisdnMaskerTest {

  @Test
  void keepsLastThreeDigits() {
    assertThat(MsisdnMasker.mask("8801711000123")).isEqualTo("**********123");
  }

  @Test
  void shortValuesAreNotMasked() {
    assertThat(MsisdnMasker.mask("123")).isEqualTo("123");
    assertThat(MsisdnMasker.mask("12")).isEqualTo("12");
    assertThat(MsisdnMasker.mask("1234")).isEqualTo("*234");
  }

  @Test
  void nullAndEmpty() {
    assertThat(MsisdnMasker.mask(null)).isEqualTo("null");
    assertThat(MsisdnMasker.mask("")).isEmpty();
  }
}
