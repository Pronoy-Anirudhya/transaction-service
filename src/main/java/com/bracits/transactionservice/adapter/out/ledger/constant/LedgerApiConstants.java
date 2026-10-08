package com.bracits.transactionservice.adapter.out.ledger.constant;

/**
 * Wire constants of the ledger-service internal API
 * ({@code ledger-service/openapi/ledger-api.yaml}, the authoritative contract), bean names, metric
 * tag values and log messages.
 */
public final class LedgerApiConstants {

  // ---- Paths and query parameters -------------------------------------------------------------------------------

  public static final String POSTINGS_PATH = "/internal/v1/postings";
  public static final String POSTING_PATH = "/internal/v1/postings/{postingId}";
  public static final String ACCOUNTS_PATH = "/internal/v1/accounts";
  public static final String ACCOUNT_BALANCE_PATH = "/internal/v1/accounts/{accountId}/balance";
  public static final String FUNDINGS_PATH = "/internal/v1/fundings";
  public static final String QUERY_LEGS = "legs";
  public static final String READINESS_PATH = "/actuator/health/readiness";

  /**
   * Allowed range of the lookup {@code legs} query parameter (and of legs per posting).
   */
  public static final int MIN_LEGS = 1;
  public static final int MAX_LEGS = 8;

  // ---- Ledger status and problem code strings -------------------------------------------------------------------

  public static final String STATUS_POSTED = "POSTED";
  public static final String STATUS_NOT_FOUND = "NOT_FOUND";
  /**
   * 422 codes; all definitive.
   */
  public static final String CODE_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
  public static final String CODE_ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
  public static final String CODE_PREVIOUSLY_REJECTED = "PREVIOUSLY_REJECTED";

  // ---- Bean names (unique to the ledger client) -----------------------------------------------------------------

  public static final String HTTP_CLIENT_BEAN = "ledgerHttpClient";
  public static final String REQUEST_FACTORY_BEAN = "ledgerClientHttpRequestFactory";
  public static final String REST_CLIENT_BEAN = "ledgerRestClient";
  public static final String RETRY_TEMPLATE_BEAN = "ledgerRetryTemplate";

  // ---- Metric tag values for ledger.posting.duration{outcome} ---------------------------------------------------

  public static final String OUTCOME_POSTED = "posted";
  public static final String OUTCOME_REJECTED = "rejected";
  public static final String OUTCOME_TIMEOUT = "timeout";
  public static final String OUTCOME_CONFLICT = "conflict";
  public static final String OUTCOME_ERROR = "error";
  public static final String OUTCOME_OVERLOADED = "overloaded";

  // ---- Log and exception messages (never include bodies) --------------------------------------------------------

  public static final String LOG_POSTING_CONFLICT =
      "ALERT ledger posting conflict (409): same postingId, different content; postingId={}";
  public static final String LOG_POSTING_ERROR = "ALERT unexpected ledger posting answer; postingId={} status={}";
  public static final String LOG_POSTING_UNPARSABLE = "ALERT unparsable ledger posting answer; postingId={} status={}";
  public static final String LOG_POSTING_FAILED = "ALERT ledger posting failed unexpectedly; postingId={}";
  public static final String LOG_POSTING_TIMEOUT =
      "Ledger posting outcome unknown after retries; postingId={} attempts={} cause={}";
  public static final String MSG_TRANSIENT_STATUS = "Ledger answered %d (outcome unknown, retryable)";
  public static final String MSG_BUDGET_EXHAUSTED =
      "Ledger budget exhausted: %d ms left is less than one read timeout (%d ms)";
  public static final String MSG_CALL_FAILED = "Ledger call failed after %d attempt(s)";
  public static final String MSG_UNEXPECTED_STATUS = "Unexpected ledger answer %d for %s";
  public static final String MSG_UNPARSABLE = "Unparsable ledger answer %d for %s";
  public static final String MSG_ACCOUNT_NOT_FOUND = "Ledger account not found: %s";
  public static final String MSG_ACCOUNT_CONFLICT = "Ledger account exists with different fields: %s";
  public static final String MSG_FUNDING_CONFLICT = "Ledger funding exists with different content: %s";
  public static final String MSG_FUNDING_REJECTED = "Ledger rejected funding %s with code %s";
  public static final String MSG_UNKNOWN_LOOKUP_STATUS = "Unknown ledger lookup status %s for posting %s";
  public static final String MSG_LEDGER_DOWN = "ledger-service is unavailable";
  public static final String LOG_LEDGER_DOWN =
      "ALERT ledger-service became unavailable; ledger-dependent requests answer 503 LEDGER_UNAVAILABLE";
  public static final String LOG_LEDGER_UP = "ledger-service is available again";
  public static final String HEALTH_DETAIL_REASON = "reason";
  public static final String MSG_INVALID_LEG_COUNT = "legCount must be %d..%d but was %d";

  private LedgerApiConstants() {
  }
}
