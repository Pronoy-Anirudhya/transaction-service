package com.bracits.transactionservice.application.constant;

/**
 * Constants of the application layer: hashing, quote-token format and log/alert messages.
 */
public final class ApplicationConstants {

  // Request hash (FR-03)
  public static final String HASH_ALGORITHM = "SHA-256";
  public static final char FIELD_SEPARATOR = '\u001f';
  public static final String ABSENT_FIELD = "\u0000";

  // Quote token: base64url(payload) "." base64url(HMAC-SHA256(payload))
  public static final String HMAC_ALGORITHM = "HmacSHA256";
  public static final String TOKEN_PART_SEPARATOR = ".";
  public static final String TOKEN_PART_SEPARATOR_REGEX = "\\.";
  public static final String CLAIM_SEPARATOR = "|";
  public static final String CLAIM_SEPARATOR_REGEX = "\\|";
  public static final int TOKEN_PARTS = 2;
  public static final int CLAIM_COUNT = 5;

  // Log messages (never bodies; MSISDNs masked)
  public static final String LOG_SEND_OUTCOME = "send-money outcome={} code={} durationMs={}";
  public static final String LOG_LIMIT_EXCEEDED = "limit exceeded for sender {}";
  public static final String LOG_LEDGER_UNKNOWN = "ledger outcome unknown ({}); left INITIATED for repair";
  public static final String LOG_LEDGER_CALL_FAILED = "ledger call failed unexpectedly; treating outcome as unknown";
  public static final String LOG_FINALISE_FAILED = "finalisation failed (PostgreSQL?); row stays INITIATED for repair";
  public static final String LOG_ALREADY_FINALISED = "row already finalised by another worker; returning its state";
  public static final String LOG_REPLAY_LOOKUP_FAILED = "idempotency look-up after a rejection failed";
  public static final String LOG_REPAIR_CLAIM_FAILED = "repair claim failed (PostgreSQL unavailable?); retrying next run";
  public static final String LOG_REPAIR_PAUSED = "repair paused: ledger-service is unavailable";
  public static final String MSG_LEDGER_UNAVAILABLE = "ledger-service is unavailable";
  public static final String LOG_REPAIR_CLAIMED = "repair claimed {} in-doubt transactions";
  public static final String LOG_REPAIR_FAILED = "repair of a transaction failed; it will be retried after the lease";
  public static final String LOG_REPAIR_RESOLVED = "repair resolved transaction as {}";
  public static final String ALERT_REPAIR_ATTEMPTS = "ALERT in-doubt transaction still unresolved after {} ledger attempts";
  public static final String ALERT_POSTING_CONFLICT = "ALERT ledger reported a posting conflict (409); investigate";
  public static final String ALERT_LEDGER_ERROR = "ALERT ledger answered with an unexpected error; outcome unknown";
  public static final String ALERT_WALLET_MISSING = "ALERT wallet {} of an in-doubt transaction is missing";
  public static final String ALERT_LEDGER_WINS =
      "ALERT HIGH reconciliation found a FAILED transaction whose legs are posted; flipped to COMPLETED (ledger wins)";
  public static final String ALERT_COMPLETED_NOT_POSTED =
      "ALERT HIGH reconciliation found a COMPLETED transaction whose legs are NOT posted";
  public static final String LOG_RECONCILIATION_LOOKUP_FAILED = "reconciliation could not look up a posting";
  public static final String LOG_REPUBLISH_CLAIMED = "republishing {} unpublished events";
  public static final String LOG_REPUBLISH_FAILED = "republish of txnId={} failed; retried after the lease: {}";
  public static final String LOG_WALLET_REGISTERED = "registered wallet {} (created={})";

  private ApplicationConstants() {
  }
}
