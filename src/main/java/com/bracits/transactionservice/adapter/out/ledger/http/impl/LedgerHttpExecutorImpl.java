package com.bracits.transactionservice.adapter.out.ledger.http.impl;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;
import com.bracits.transactionservice.adapter.out.ledger.exception.LedgerBudgetExhaustedException;
import com.bracits.transactionservice.adapter.out.ledger.exception.LedgerCallFailedException;
import com.bracits.transactionservice.adapter.out.ledger.exception.LedgerTransientStatusException;
import com.bracits.transactionservice.adapter.out.ledger.http.LedgerHttpExecutor;
import com.bracits.transactionservice.adapter.out.ledger.model.LedgerHttpResponse;
import com.bracits.transactionservice.adapter.out.ledger.retry.LedgerAttemptBudget;
import com.bracits.transactionservice.adapter.out.ledger.retry.LedgerRetryPredicate;
import com.bracits.transactionservice.config.properties.LedgerProperties;
import com.bracits.transactionservice.port.out.client.LedgerHealthPort;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient.RequestHeadersSpec.RequiredValueExchangeFunction;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Default implementation of {@link LedgerHttpExecutor}.
 */
@Component
public final class LedgerHttpExecutorImpl implements LedgerHttpExecutor {

  private static final Predicate<Throwable> RETRYABLE = new LedgerRetryPredicate();

  private static final RequiredValueExchangeFunction<LedgerHttpResponse> CAPTURE =
      (request, response) -> new LedgerHttpResponse(response.getStatusCode(),
          response.getBody().readAllBytes());

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final JsonMapper jsonMapper;
  private final Duration totalBudget;
  private final Duration readTimeout;
  private final LedgerHealthPort health;

  public LedgerHttpExecutorImpl(
      @Qualifier(LedgerApiConstants.REST_CLIENT_BEAN) RestClient restClient,
      @Qualifier(LedgerApiConstants.RETRY_TEMPLATE_BEAN) RetryTemplate retryTemplate,
      JsonMapper jsonMapper,
      LedgerProperties properties,
      LedgerHealthPort health) {
    this.restClient = restClient;
    this.retryTemplate = retryTemplate;
    this.jsonMapper = jsonMapper;
    this.totalBudget = properties.totalBudget();
    this.readTimeout = properties.readTimeout();
    this.health = health;
  }

  /**
   * POSTs JSON bytes. Throws {@link LedgerCallFailedException} when no answer was obtained.
   */
  @Override
  public LedgerHttpResponse postJson(String path, byte[] body) {
    return execute(() -> restClient.post()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchangeForRequiredValue(CAPTURE));
  }

  /**
   * As {@link #postJson}, but a missing answer becomes {@link LedgerUnavailableException}.
   */
  @Override
  public LedgerHttpResponse postJsonOrThrow(String path, byte[] body) {
    return orUnavailable(() -> postJson(path, body));
  }

  /**
   * GETs {@code uri}; a missing answer becomes {@link LedgerUnavailableException}.
   */
  @Override
  public LedgerHttpResponse getOrThrow(Function<UriBuilder, URI> uri) {
    return orUnavailable(() -> execute(() -> restClient.get()
        .uri(uri)
        .exchangeForRequiredValue(CAPTURE)));
  }

  /**
   * Serialises a wire DTO; {@link JacksonException} propagates to the caller.
   */
  @Override
  public byte[] toJson(Object dto) {
    return jsonMapper.writeValueAsBytes(dto);
  }

  /**
   * As {@link #toJson}, but a serialisation failure becomes {@link LedgerUnavailableException}.
   */
  @Override
  public byte[] toJsonOrThrow(Object dto) {
    try {
      return toJson(dto);
    } catch (JacksonException e) {
      throw new LedgerUnavailableException(e.getOriginalMessage(), e);
    }
  }

  /**
   * Parses a body; empty when it is not valid JSON of that shape.
   */
  @Override
  public <T> Optional<T> read(LedgerHttpResponse response, Class<T> type) {
    try {
      return Optional.ofNullable(jsonMapper.readValue(response.body(), type));
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  /**
   * Runs {@code call} under the ledger retry template. A 503 answer becomes a retryable exception;
   * any other answer is returned. Throws {@link LedgerCallFailedException} when no answer was
   * obtained.
   */
  private LedgerHttpResponse execute(Supplier<LedgerHttpResponse> call) {
    LedgerAttemptBudget budget = new LedgerAttemptBudget(totalBudget, readTimeout);

    try {
      return retryTemplate.execute(() -> {
        budget.beforeAttempt();

        LedgerHttpResponse response = call.get();
        if (response.is(HttpStatus.SERVICE_UNAVAILABLE)) {
          throw new LedgerTransientStatusException(response.statusValue());
        }

        return response;
      });
    } catch (RetryException e) {
      Throwable cause = e.getCause();
      boolean transientFailure =
          RETRYABLE.test(cause) || cause instanceof LedgerBudgetExhaustedException;

      throw new LedgerCallFailedException(budget.attempts(), transientFailure, cause);
    }
  }

  /**
   * Fails fast while ledger-service is known to be down; otherwise a call that got no answer
   * becomes {@link LedgerUnavailableException}.
   */
  private LedgerHttpResponse orUnavailable(Supplier<LedgerHttpResponse> call) {
    if (!health.isAvailable()) {
      throw new LedgerUnavailableException(LedgerApiConstants.MSG_LEDGER_DOWN);
    }

    try {
      return call.get();
    } catch (LedgerCallFailedException e) {
      throw new LedgerUnavailableException(e.getMessage(), e.getCause());
    }
  }

}
