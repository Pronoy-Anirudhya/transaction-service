package com.bracits.transactionservice.domain;

/**
 * A non-negative amount in minor units (poisha). Money is always {@code long}; never floating point or BigDecimal.
 * All arithmetic is overflow-checked.
 */
public record Money(long minor) {

  public static final Money ZERO = new Money(0L);

  public Money {
    if (minor < 0) {
      throw new IllegalArgumentException(DomainMessages.NEGATIVE_AMOUNT.formatted(minor));
    }
  }

  public static Money of(long minor) {
    return new Money(minor);
  }

  public Money plus(Money other) {
    return new Money(Math.addExact(minor, other.minor));
  }

  public Money minus(Money other) {
    return new Money(Math.subtractExact(minor, other.minor));
  }

  public boolean isZero() {
    return minor == 0L;
  }

  public boolean isPositive() {
    return minor > 0L;
  }
}
