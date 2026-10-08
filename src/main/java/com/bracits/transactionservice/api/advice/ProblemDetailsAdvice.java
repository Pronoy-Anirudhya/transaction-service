package com.bracits.transactionservice.api.advice;

import com.bracits.transactionservice.api.constant.ApiConstants;
import com.bracits.transactionservice.api.constant.ApiMessages;
import com.bracits.transactionservice.api.dto.response.FieldError;
import com.bracits.transactionservice.api.enums.ApiErrorCode;
import com.bracits.transactionservice.api.exception.ApiException;
import com.bracits.transactionservice.api.mapper.ProblemMapper;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.port.out.exception.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.exception.LedgerConflictException;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every error as RFC 9457 Problem Details with a {@code code} (spec 7).
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  private final ProblemMapper problems;

  public ProblemDetailsAdvice(ProblemMapper problems) {
    this.problems = problems;
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApi(ApiException e) {
    return problems.toResponse(problems.toProblem(e.code(), e.getMessage()));
  }

  /**
   * A bulkhead ({@code @ConcurrencyLimit}) is saturated: shed load at once (NFR-07, P10).
   */
  @ExceptionHandler(InvocationRejectedException.class)
  ResponseEntity<ProblemDetail> handleOverloaded(InvocationRejectedException e) {
    LOG.warn(ApiMessages.LOG_OVERLOADED, e.getMessage());
    return problems.toRetryLaterResponse(
        problems.toProblem(ApiErrorCode.OVERLOADED, ApiMessages.OVERLOADED));
  }

  /**
   * PostgreSQL or the ledger is unavailable before any money moved (F7).
   */
  @ExceptionHandler({DataAccessException.class, TransactionException.class})
  ResponseEntity<ProblemDetail> handleUnavailable(RuntimeException e) {
    LOG.warn(ApiMessages.LOG_UNAVAILABLE, e.getClass().getSimpleName());
    return problems.toRetryLaterResponse(
        problems.toProblem(ApiErrorCode.SERVICE_UNAVAILABLE, ApiMessages.UNAVAILABLE));
  }

  /**
   * ledger-service (a separate service) is unreachable: say so explicitly, with Retry-After.
   */
  @ExceptionHandler(LedgerUnavailableException.class)
  ResponseEntity<ProblemDetail> handleLedgerUnavailable(LedgerUnavailableException e) {
    LOG.warn(ApiMessages.LOG_UNAVAILABLE, e.getClass().getSimpleName());
    return problems.toRetryLaterResponse(
        problems.toProblem(ApiErrorCode.LEDGER_UNAVAILABLE, ApiMessages.LEDGER_UNAVAILABLE));
  }

  /**
   * The ledger has a funding/account with this ID but different content: the key was reused.
   */
  @ExceptionHandler(LedgerConflictException.class)
  ResponseEntity<ProblemDetail> handleLedgerConflict(LedgerConflictException e) {
    ProblemDetail problem = problems.toProblem(FailureCode.IDEMPOTENCY_CONFLICT, Optional.empty());
    problem.setDetail(ApiMessages.REQUEST_REUSED);
    return problems.toResponse(problem);
  }

  @ExceptionHandler(LedgerAccountNotFoundException.class)
  ResponseEntity<ProblemDetail> handleLedgerAccountNotFound(LedgerAccountNotFoundException e) {
    return problems.toResponse(
        problems.toProblem(ApiErrorCode.NOT_FOUND, ApiMessages.WALLET_NOT_FOUND));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
    LOG.error(ApiMessages.LOG_UNEXPECTED, e);
    return problems.toResponse(
        problems.toProblem(ApiErrorCode.INTERNAL_ERROR, ApiMessages.INTERNAL_ERROR));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status,
      WebRequest request) {
    return withErrors(super.handleMethodArgumentNotValid(ex, headers, status, request),
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
            .toList());
  }

  /**
   * Spring 7 validates the whole handler method when any parameter carries a constraint (e.g. the
   * Idempotency-Key header), so body errors arrive here too.
   */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status,
      WebRequest request) {
    List<FieldError> errors = ex.getParameterValidationResults().stream()
        .flatMap(ProblemDetailsAdvice::fieldErrors)
        .toList();

    return withErrors(super.handleHandlerMethodValidationException(ex, headers, status, request),
        errors);
  }

  private static Stream<FieldError> fieldErrors(ParameterValidationResult result) {
    if (result instanceof ParameterErrors errors) {
      return errors.getFieldErrors().stream()
          .map(error -> new FieldError(error.getField(), error.getDefaultMessage()));
    }

    String parameter = result.getMethodParameter().getParameterName();
    return result.getResolvableErrors().stream()
        .map(error -> new FieldError(parameter, error.getDefaultMessage()));
  }

  private static ResponseEntity<Object> withErrors(ResponseEntity<Object> response,
      List<FieldError> errors) {
    if (response != null && response.getBody() instanceof ProblemDetail problem
        && !errors.isEmpty()) {
      problem.setProperty(ApiConstants.PROBLEM_PROPERTY_ERRORS, errors);
    }
    return response;
  }

  /**
   * Adds a {@code code} to the Problem Details Spring MVC builds for its own exceptions (400, 404,
   * 405, 415…).
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode,
        request);

    if (response != null && response.getBody() instanceof ProblemDetail problem
        && (problem.getProperties() == null
        || !problem.getProperties().containsKey(ApiConstants.PROBLEM_PROPERTY_CODE))) {
      problem.setProperty(ApiConstants.PROBLEM_PROPERTY_CODE,
          ApiErrorCode.forStatus(statusCode.value()).name());
    }
    return response;
  }
}
