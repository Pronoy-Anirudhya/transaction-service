package com.bracits.transactionservice.application.enums;

import java.util.Locale;

/**
 * Values of the {@code outcome} tag.
 */
public enum MetricOutcome {
  COMPLETED, PROCESSING, REJECTED, INVALID, UNAVAILABLE, FAILED, IN_DOUBT;

  public String tag() {
    return name().toLowerCase(Locale.ROOT);
  }
}
