package com.bracits.transactionservice.api.mapper;

import com.bracits.transactionservice.api.enums.ApiErrorCode;
import com.bracits.transactionservice.domain.enums.FailureCode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Builds RFC 9457 Problem Details with a machine-readable {@code code} (spec 7).
 */
public interface ProblemMapper {

  ProblemDetail toProblem(ApiErrorCode code, String detail);

  /**
   * 409 for {@code IDEMPOTENCY_CONFLICT} / {@code QUOTE_CHANGED}, 422 for every other business
   * failure.
   */
  ProblemDetail toProblem(FailureCode code, Optional<UUID> txnId);

  ProblemDetail toProblem(HttpStatusCode status, String code, String detail);

  <T> ResponseEntity<T> toResponse(ProblemDetail problem, Class<T> bodyType);

  ResponseEntity<ProblemDetail> toResponse(ProblemDetail problem);

  /**
   * 503 with {@code Retry-After: 1} (NFR-07).
   */
  ResponseEntity<ProblemDetail> toRetryLaterResponse(ProblemDetail problem);

  /**
   * Flat JSON object of a problem, for writers outside Spring MVC (servlet filters).
   */
  Map<String, Object> toMap(ProblemDetail problem);

  int statusOf(FailureCode code);
}
