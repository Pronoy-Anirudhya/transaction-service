package com.bracits.transactionservice.adapter.out.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A manually advanced Caffeine clock.
 */
final class FakeTicker implements Ticker {

  private final AtomicLong nanos = new AtomicLong();

  @Override
  public long read() {
    return nanos.get();
  }

  void advance(Duration duration) {
    nanos.addAndGet(duration.toNanos());
  }
}
