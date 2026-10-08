package com.bracits.transactionservice.application.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BoundedParallelTest {

  @Test
  void resultsKeepItemOrder() {
    List<Integer> items = IntStream.range(0, 50).boxed().toList();

    assertThat(BoundedParallel.map(items, 8, i -> i * 2)).isEqualTo(
        items.stream().map(i -> i * 2).toList());
  }

  @Test
  void neverRunsMoreThanParallelismAtOnce() {
    AtomicInteger running = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();

    BoundedParallel.forEach(IntStream.range(0, 40).boxed().toList(), 3, i -> {
      peak.accumulateAndGet(running.incrementAndGet(), Math::max);
      sleepBriefly();
      running.decrementAndGet();
    });

    assertThat(peak.get()).isBetween(1, 3);
  }

  @Test
  void taskFailureIsRethrown() {
    assertThatThrownBy(() -> BoundedParallel.map(List.of(1), 1, i -> {
      throw new IllegalArgumentException("boom");
    })).isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  private static void sleepBriefly() {
    try {
      Thread.sleep(5);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
