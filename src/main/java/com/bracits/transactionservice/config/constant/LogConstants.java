package com.bracits.transactionservice.config.constant;

/**
 * MDC keys and log field names (spec 12, observability).
 */
public final class LogConstants {

  public static final String MDC_TXN_ID = "txnId";
  public static final String MDC_OUTCOME = "outcome";
  public static final String MDC_CODE = "code";
  public static final String MDC_DURATION_MS = "durationMs";

  private LogConstants() {
  }
}
