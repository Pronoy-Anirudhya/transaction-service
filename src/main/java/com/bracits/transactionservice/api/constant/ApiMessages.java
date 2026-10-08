package com.bracits.transactionservice.api.constant;

/**
 * Problem Details {@code detail} texts and API log messages.
 */
public final class ApiMessages {

  public static final String UNAUTHORIZED = "Missing or invalid API key";
  public static final String TXN_NOT_FOUND = "No transaction with this txnId";
  public static final String WALLET_NOT_FOUND = "No wallet with this MSISDN";
  public static final String OVERLOADED = "The service is saturated; retry after the Retry-After delay";
  public static final String LEDGER_UNAVAILABLE =
      "ledger-service is unavailable. No money was moved; retry after the Retry-After delay";
  public static final String PROCESSING =
      "The ledger has not confirmed this transfer yet (ledger-service is slow or unavailable). "
          + "It is completed or failed automatically; poll GET /api/v1/send-money/{txnId}";
  public static final String UNAVAILABLE = "A dependency is unavailable; retry after the Retry-After delay";
  public static final String INTERNAL_ERROR = "Unexpected error";
  public static final String INVALID_QUOTE_TOKEN = "The quote token is malformed or its signature is invalid";
  public static final String MSISDN_EXISTS = "The MSISDN is already registered with different details";
  public static final String INVALID_WINDOW = "'from' must be before 'to'";
  public static final String REQUEST_REUSED = "The Idempotency-Key was already used with a different request";

  public static final String FAILURE_INSUFFICIENT_FUNDS = "The sender's available balance is too low";
  public static final String FAILURE_LIMIT_EXCEEDED = "The sender's daily or monthly limit would be exceeded";
  public static final String FAILURE_AMOUNT_OUT_OF_RANGE = "The amount is outside the allowed range for the sender";
  public static final String FAILURE_SELF_TRANSFER = "Sender and receiver must differ";
  public static final String FAILURE_WALLET_NOT_FOUND = "The sender or receiver wallet does not exist";
  public static final String FAILURE_WALLET_INACTIVE = "The sender or receiver wallet is not active";
  public static final String FAILURE_RECEIVER_NOT_ALLOWED = "The receiver cannot receive Send Money";
  public static final String FAILURE_QUOTE_CHANGED = "The quote expired, does not match the request, or the fee changed";
  public static final String FAILURE_IDEMPOTENCY_CONFLICT = "The Idempotency-Key was already used with a different body";
  public static final String FAILURE_LEDGER_REJECTED = "The ledger rejected the posting";

  public static final String LOG_UNAVAILABLE = "dependency unavailable: {}";
  public static final String LOG_OVERLOADED = "bulkhead saturated: {}";
  public static final String LOG_UNEXPECTED = "unexpected error";

  private ApiMessages() {
  }
}
