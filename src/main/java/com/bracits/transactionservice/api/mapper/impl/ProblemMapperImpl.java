package com.bracits.transactionservice.api.mapper.impl;

import com.bracits.transactionservice.api.constant.ApiConstants;
import com.bracits.transactionservice.api.constant.ApiMessages;
import com.bracits.transactionservice.api.enums.ApiErrorCode;
import com.bracits.transactionservice.api.mapper.ProblemMapper;
import com.bracits.transactionservice.domain.enums.FailureCode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link ProblemMapper}.
 */
@Component
public final class ProblemMapperImpl implements ProblemMapper {

  private static final String FIELD_TYPE = "type";
  private static final String FIELD_TITLE = "title";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_DETAIL = "detail";
  private static final String FIELD_INSTANCE = "instance";

  @Override
  public ProblemDetail toProblem(ApiErrorCode code, String detail) {
    return toProblem(HttpStatusCode.valueOf(code.httpStatus()), code.name(), detail);
  }

  /**
   * 409 for {@code IDEMPOTENCY_CONFLICT} / {@code QUOTE_CHANGED}, 422 for every other business
   * failure.
   */
  @Override
  public ProblemDetail toProblem(FailureCode code, Optional<UUID> txnId) {
    ProblemDetail problem = toProblem(HttpStatusCode.valueOf(statusOf(code)), code.name(),
        detailOf(code));
    txnId.ifPresent(id -> problem.setProperty(ApiConstants.PROBLEM_PROPERTY_TXN_ID, id));
    return problem;
  }

  @Override
  public ProblemDetail toProblem(HttpStatusCode status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(ApiConstants.PROBLEM_TYPE_PREFIX + code.toLowerCase(Locale.ROOT)));

    HttpStatus known = HttpStatus.resolve(status.value());
    if (known != null) {
      problem.setTitle(known.getReasonPhrase());
    }

    problem.setProperty(ApiConstants.PROBLEM_PROPERTY_CODE, code);
    return problem;
  }

  @Override
  public <T> ResponseEntity<T> toResponse(ProblemDetail problem, Class<T> bodyType) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(bodyType.cast(problem));
  }

  @Override
  public ResponseEntity<ProblemDetail> toResponse(ProblemDetail problem) {
    return toResponse(problem, ProblemDetail.class);
  }

  /**
   * 503 with {@code Retry-After: 1} (NFR-07).
   */
  @Override
  public ResponseEntity<ProblemDetail> toRetryLaterResponse(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .header(HttpHeaders.RETRY_AFTER, ApiConstants.RETRY_AFTER_SECONDS)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /**
   * Flat JSON object of a problem, for writers outside Spring MVC (servlet filters).
   */
  @Override
  public Map<String, Object> toMap(ProblemDetail problem) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(FIELD_TYPE, problem.getType().toString());
    map.put(FIELD_TITLE, problem.getTitle());
    map.put(FIELD_STATUS, problem.getStatus());
    map.put(FIELD_DETAIL, problem.getDetail());

    if (problem.getInstance() != null) {
      map.put(FIELD_INSTANCE, problem.getInstance().toString());
    }
    if (problem.getProperties() != null) {
      map.putAll(problem.getProperties());
    }
    return map;
  }

  @Override
  public int statusOf(FailureCode code) {
    return switch (code) {
      case IDEMPOTENCY_CONFLICT, QUOTE_CHANGED -> HttpStatus.CONFLICT.value();
      case INSUFFICIENT_FUNDS, LIMIT_EXCEEDED, AMOUNT_OUT_OF_RANGE, SELF_TRANSFER, WALLET_NOT_FOUND,
           WALLET_INACTIVE,
           RECEIVER_NOT_ALLOWED, LEDGER_REJECTED -> HttpStatus.UNPROCESSABLE_CONTENT.value();
    };
  }

  private static String detailOf(FailureCode code) {
    return switch (code) {
      case INSUFFICIENT_FUNDS -> ApiMessages.FAILURE_INSUFFICIENT_FUNDS;
      case LIMIT_EXCEEDED -> ApiMessages.FAILURE_LIMIT_EXCEEDED;
      case AMOUNT_OUT_OF_RANGE -> ApiMessages.FAILURE_AMOUNT_OUT_OF_RANGE;
      case SELF_TRANSFER -> ApiMessages.FAILURE_SELF_TRANSFER;
      case WALLET_NOT_FOUND -> ApiMessages.FAILURE_WALLET_NOT_FOUND;
      case WALLET_INACTIVE -> ApiMessages.FAILURE_WALLET_INACTIVE;
      case RECEIVER_NOT_ALLOWED -> ApiMessages.FAILURE_RECEIVER_NOT_ALLOWED;
      case QUOTE_CHANGED -> ApiMessages.FAILURE_QUOTE_CHANGED;
      case IDEMPOTENCY_CONFLICT -> ApiMessages.FAILURE_IDEMPOTENCY_CONFLICT;
      case LEDGER_REJECTED -> ApiMessages.FAILURE_LEDGER_REJECTED;
    };
  }
}
