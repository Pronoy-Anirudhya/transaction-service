package com.bracits.transactionservice.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TxnStatusTest {

  @ParameterizedTest(name = "{0} -> {1}: {2}")
  @CsvSource({
      "INITIATED, INITIATED, false",
      "INITIATED, COMPLETED, true",
      "INITIATED, FAILED,    true",
      "COMPLETED, INITIATED, false",
      "COMPLETED, COMPLETED, false",
      "COMPLETED, FAILED,    false",
      "FAILED,    INITIATED, false",
      "FAILED,    COMPLETED, true",
      "FAILED,    FAILED,    false"
  })
  void transitions(TxnStatus from, TxnStatus to, boolean allowed) {
    assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    if (allowed) {
      assertThat(from.transitionTo(to)).isEqualTo(to);
    } else {
      assertThatThrownBy(() -> from.transitionTo(to))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(from.name())
          .hasMessageContaining(to.name());
    }
  }

  @Test
  void onlyInitiatedIsNotFinal() {
    assertThat(TxnStatus.INITIATED.isFinal()).isFalse();
    assertThat(TxnStatus.COMPLETED.isFinal()).isTrue();
    assertThat(TxnStatus.FAILED.isFinal()).isTrue();
  }
}
