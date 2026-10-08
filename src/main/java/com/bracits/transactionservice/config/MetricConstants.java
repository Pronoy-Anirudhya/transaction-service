package com.bracits.transactionservice.config;

/** Metric names and tags (spec 12). Micrometer names use dots; Prometheus renders them with underscores. */
public final class MetricConstants {

  public static final String SENDMONEY_REQUESTS = "sendmoney.requests";
  public static final String SENDMONEY_DURATION = "sendmoney.duration";
  public static final String LEDGER_POSTING_DURATION = "ledger.posting.duration";
  public static final String TXN_IN_DOUBT = "txn.in.doubt";
  public static final String TXN_REPAIR_ATTEMPTS = "txn.repair.attempts";
  public static final String EVENTS_PUBLISH = "events.publish";
  public static final String EVENTS_UNPUBLISHED = "events.unpublished";
  public static final String LIMIT_REJECTIONS = "limit.rejections";
  public static final String RECONCILIATION_MISMATCHES = "reconciliation.mismatches";

  public static final String TAG_OUTCOME = "outcome";
  public static final String TAG_CODE = "code";
  public static final String TAG_RESULT = "result";

  public static final String RESULT_ACK = "ack";
  public static final String RESULT_NACK = "nack";
  public static final String RESULT_RETURNED = "returned";
  public static final String RESULT_TIMEOUT = "timeout";
  public static final String RESULT_ERROR = "error";

  public static final String NONE = "none";

  private MetricConstants() {
  }
}
