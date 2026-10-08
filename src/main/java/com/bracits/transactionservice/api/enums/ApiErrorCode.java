package com.bracits.transactionservice.api.enums;

/**
 * Problem Details {@code code} values that are not business failure codes. Business failures use
 * {@link com.bracits.transactionservice.domain.enums.FailureCode} (422 / 409).
 */
public enum ApiErrorCode {
  /**
   * 400: Bean Validation failed, malformed body, or missing / too long Idempotency-Key.
   */
  VALIDATION_FAILED(400),
  /**
   * 400: the quote token is malformed or its signature is wrong.
   */
  INVALID_QUOTE_TOKEN(400),
  /**
   * 401: missing or wrong API key.
   */
  UNAUTHORIZED(401),
  /**
   * 404: unknown transaction or wallet.
   */
  NOT_FOUND(404),
  /**
   * 409: test-profile wallet registration with an MSISDN that exists with different fields.
   */
  MSISDN_EXISTS(409),
  /**
   * 503: a bulkhead is saturated; retry after {@code Retry-After}.
   */
  OVERLOADED(503),
  /**
   * 503: a dependency (PostgreSQL, ledger) is unavailable before any money moved.
   */
  SERVICE_UNAVAILABLE(503),
  /**
   * 503: ledger-service (a separate service) is unavailable; no money moved. Retry after
   * {@code Retry-After}.
   */
  LEDGER_UNAVAILABLE(503),
  /**
   * 500: unexpected error.
   */
  INTERNAL_ERROR(500);

  private final int httpStatus;

  ApiErrorCode(int httpStatus) {
    this.httpStatus = httpStatus;
  }

  public int httpStatus() {
    return httpStatus;
  }

  /**
   * Code for framework-generated errors (e.g. 405, 415, malformed JSON) that carry no code of their
   * own.
   */
  public static ApiErrorCode forStatus(int status) {
    return switch (status) {
      case 401 -> UNAUTHORIZED;
      case 404 -> NOT_FOUND;
      case 503 -> SERVICE_UNAVAILABLE;
      default -> status >= 500 ? INTERNAL_ERROR : VALIDATION_FAILED;
    };
  }
}
