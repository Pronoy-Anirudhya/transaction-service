package com.bracits.transactionservice.config;

/** Configuration property prefixes and the placeholders used in annotations. */
public final class PropertyConstants {

  public static final String LEDGER = "poc.ledger";
  public static final String BUSINESS = "poc.business";
  public static final String QUOTE = "poc.quote";
  public static final String CACHE = "poc.cache";
  public static final String REPAIR = "poc.repair";
  public static final String EVENTS = "poc.events";
  public static final String SECURITY = "poc.security";
  public static final String RECONCILIATION = "poc.reconciliation";
  public static final String API = "poc.api";

  /** For {@code @ConcurrencyLimit(limitString = …)} on the ledger port (P10). */
  public static final String LEDGER_CONCURRENCY_LIMIT_PLACEHOLDER = "${poc.ledger.concurrency-limit}";

  /** For {@code @ConcurrencyLimit(limitString = …)} on the posting endpoint (P10). */
  public static final String SEND_MONEY_CONCURRENCY_LIMIT_PLACEHOLDER = "${poc.api.send-money-concurrency-limit}";

  /** For {@code @Scheduled}: repair worker period (spec 8.4). */
  public static final String REPAIR_INTERVAL_PLACEHOLDER = "${poc.repair.interval}";

  /** For {@code @Scheduled}: publish-mark flusher period (spec 9 rule 4). */
  public static final String EVENTS_FLUSH_INTERVAL_PLACEHOLDER = "${poc.events.flush-interval}";

  /** For {@code @Scheduled}: republisher period (spec 9 rule 6). */
  public static final String EVENTS_REPUBLISH_INTERVAL_PLACEHOLDER = "${poc.events.republish.interval}";

  /** For {@code @Profile}: test-profile support endpoints (FR-09). */
  public static final String PROFILE_TEST = "test";

  private PropertyConstants() {
  }
}
