package com.bracits.transactionservice.application.fakes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A fixed clock that a test can move forward.
 */
public final class MutableClock extends Clock {

  private volatile Instant now;
  private final ZoneId zone;

  public MutableClock(Instant now) {
    this(now, ZoneOffset.UTC);
  }

  private MutableClock(Instant now, ZoneId zone) {
    this.now = now;
    this.zone = zone;
  }

  public void advance(Duration duration) {
    now = now.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId newZone) {
    return Clock.fixed(now, newZone);
  }

  @Override
  public Instant instant() {
    return now;
  }
}
