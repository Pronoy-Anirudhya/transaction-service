package com.bracits.transactionservice.api.dto;

/** Transaction status as exposed by the API. INITIATED is shown as PROCESSING. */
public enum ApiTxnStatus {
  PROCESSING,
  COMPLETED,
  FAILED
}
