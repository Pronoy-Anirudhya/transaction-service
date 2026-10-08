package com.bracits.transactionservice.api.exception;

import com.bracits.transactionservice.api.advice.ProblemDetailsAdvice;
import com.bracits.transactionservice.api.enums.ApiErrorCode;

/**
 * An API error with a Problem Details {@code code}; rendered by {@link ProblemDetailsAdvice}.
 */
public class ApiException extends RuntimeException {

  private final ApiErrorCode code;

  public ApiException(ApiErrorCode code, String detail) {
    super(detail);
    this.code = code;
  }

  public ApiErrorCode code() {
    return code;
  }
}
