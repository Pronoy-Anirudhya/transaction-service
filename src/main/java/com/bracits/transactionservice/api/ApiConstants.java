package com.bracits.transactionservice.api;

/** Public API paths, headers, validation patterns and Problem Details fields (spec 7.1). */
public final class ApiConstants {

  // Paths
  public static final String API_V1 = "/api/v1";
  public static final String API_PATH_PATTERN = "/api/**";
  public static final String API_PREFIX = "/api/";
  public static final String SEND_MONEY_PATH = API_V1 + "/send-money";
  public static final String QUOTE_PATH = SEND_MONEY_PATH + "/quote";
  public static final String TXN_ID_VARIABLE = "txnId";
  public static final String TXN_BY_ID_PATH = "/{" + TXN_ID_VARIABLE + "}";
  public static final String RECONCILIATION_PATH = API_V1 + "/admin/reconciliation";
  public static final String WALLETS_PATH = API_V1 + "/wallets";
  public static final String MSISDN_VARIABLE = "msisdn";
  public static final String WALLET_FUND_PATH = "/{" + MSISDN_VARIABLE + "}/fund";
  public static final String WALLET_BALANCE_PATH = "/{" + MSISDN_VARIABLE + "}/balance";
  public static final String PARAM_FROM = "from";
  public static final String PARAM_TO = "to";

  // Contracts served by this service
  public static final String OPENAPI_PATH = "/openapi.yaml";
  public static final String OPENAPI_CLASSPATH_LOCATION = "openapi/transaction-api.yaml";
  public static final String EVENT_SCHEMA_CLASSPATH_LOCATION = "openapi/events/send-money-v1.schema.json";
  public static final String MEDIA_TYPE_YAML = "application/yaml";

  // Response values
  public static final String FUNDING_STATUS_POSTED = "POSTED";

  // Headers
  public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
  public static final String HEADER_API_KEY = "X-API-Key";
  public static final String RETRY_AFTER_SECONDS = "1";

  // Validation
  public static final String MSISDN_REGEX = "^01[3-9][0-9]{8}$";
  public static final String CURRENCY_REGEX = "^BDT$";
  public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;
  public static final int REFERENCE_MAX_LENGTH = 50;
  public static final int HOLDER_NAME_MAX_LENGTH = 100;
  public static final int QUOTE_TOKEN_MAX_LENGTH = 512;
  public static final int MIN_KYC_TIER = 1;

  // RFC 9457 Problem Details
  public static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:mfs:";
  public static final String PROBLEM_PROPERTY_CODE = "code";
  public static final String PROBLEM_PROPERTY_TXN_ID = "txnId";
  public static final String PROBLEM_PROPERTY_ERRORS = "errors";

  private ApiConstants() {
  }
}
