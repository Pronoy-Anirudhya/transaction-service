package com.bracits.transactionservice.adapter.out.ledger;

import com.bracits.transactionservice.adapter.out.ledger.dto.BalanceResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.LedgerProblemDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingLookupResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.mapper.LedgerDtoMapper;
import com.bracits.transactionservice.config.LedgerProperties;
import com.bracits.transactionservice.config.MetricConstants;
import com.bracits.transactionservice.config.PropertyConstants;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.ledger.AccountBalance;
import com.bracits.transactionservice.domain.ledger.AccountCreation;
import com.bracits.transactionservice.domain.ledger.LedgerAccount;
import com.bracits.transactionservice.domain.ledger.PostingLookup;
import com.bracits.transactionservice.domain.ledger.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.PostingOutcome.Unknown;
import com.bracits.transactionservice.domain.ledger.PostingRequest;
import com.bracits.transactionservice.domain.ledger.UnknownReason;
import com.bracits.transactionservice.port.out.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.LedgerAccountsPort;
import com.bracits.transactionservice.port.out.LedgerConflictException;
import com.bracits.transactionservice.port.out.LedgerPort;
import com.bracits.transactionservice.port.out.LedgerQueryPort;
import com.bracits.transactionservice.port.out.LedgerUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Proxyable;
import org.springframework.context.annotation.ProxyType;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.RequiredValueExchangeFunction;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * HTTP adapter for the ledger-service internal API ({@code ledger-service/openapi/ledger-api.yaml}; spec 7.2, 8.3).
 * Answers are classified by HTTP status first, then by the problem {@code code}. Every call is retried only on 503
 * ({@code LEDGER_TIMEOUT} or {@code OVERLOADED}) / I/O error / timeout, at most {@code maxRetries} times, within the
 * total budget, and a retry starts only while at least one read timeout of budget is left (decision B7). Each retry
 * resends the identical body bytes. Ledger answers never throw from {@link #post}; the other calls translate failures
 * into the port exceptions. Bodies are never logged.
 *
 * <p>{@code Retry-After: 1} on a 503 is deliberately not slept inside the request: the spec 8.3 backoff (50 ms ×2 with
 * jitter) and the 3 s budget take precedence, since a 1 s wait would leave no room for a full read timeout. Once the
 * budget is spent the outcome stays unknown and the repair worker re-sends later (its backoff starts at 1 s, which
 * honours the hint).
 *
 * <p>Deliberately <em>not</em> final: {@code @ConcurrencyLimit} on {@link #post} needs a class-based (CGLIB) proxy.
 * In Spring Framework 7.0.9 the concurrency interceptor reads the annotation from the invoked method, which on a JDK
 * interface proxy is {@link LedgerPort#post} (unannotated), so the call would fail with "No @ConcurrencyLimit
 * annotation found". {@link Proxyable} pins the class proxy regardless of {@code spring.aop.proxy-target-class}.
 */
@Component
@Proxyable(ProxyType.TARGET_CLASS)
public class HttpLedgerClient implements LedgerPort, LedgerQueryPort, LedgerAccountsPort {

  private static final Logger log = LoggerFactory.getLogger(HttpLedgerClient.class);

  private static final Predicate<Throwable> RETRYABLE = new LedgerRetryPredicate();

  private static final RequiredValueExchangeFunction<LedgerHttpResponse> CAPTURE =
      (request, response) -> new LedgerHttpResponse(response.getStatusCode(), response.getBody().readAllBytes());

  private final RestClient restClient;
  private final RetryTemplate retryTemplate;
  private final LedgerDtoMapper mapper;
  private final JsonMapper jsonMapper;
  private final MeterRegistry meterRegistry;
  private final Duration totalBudget;
  private final Duration readTimeout;

  public HttpLedgerClient(
      @Qualifier(LedgerApiConstants.REST_CLIENT_BEAN) RestClient restClient,
      @Qualifier(LedgerApiConstants.RETRY_TEMPLATE_BEAN) RetryTemplate retryTemplate,
      LedgerDtoMapper mapper,
      JsonMapper jsonMapper,
      MeterRegistry meterRegistry,
      LedgerProperties properties) {
    this.restClient = restClient;
    this.retryTemplate = retryTemplate;
    this.mapper = mapper;
    this.jsonMapper = jsonMapper;
    this.meterRegistry = meterRegistry;
    this.totalBudget = properties.totalBudget();
    this.readTimeout = properties.readTimeout();
  }

  // ---- LedgerPort -----------------------------------------------------------------------------------------------

  @Override
  @ConcurrencyLimit(
      limitString = PropertyConstants.LEDGER_CONCURRENCY_LIMIT_PLACEHOLDER,
      policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
  public PostingOutcome post(PostingRequest request) {
    long start = System.nanoTime();
    PostingOutcome outcome = send(request);
    Timer.builder(MetricConstants.LEDGER_POSTING_DURATION)
        .tag(MetricConstants.TAG_OUTCOME, mapper.toMetricOutcome(outcome))
        .register(meterRegistry)
        .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
    return outcome;
  }

  private PostingOutcome send(PostingRequest request) {
    UUID postingId = request.postingId();
    LedgerHttpResponse response;
    try {
      byte[] body = jsonMapper.writeValueAsBytes(mapper.toPostingRequestDto(request));
      response = execute(() -> restClient.post()
          .uri(LedgerApiConstants.POSTINGS_PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .exchangeForRequiredValue(CAPTURE));
    } catch (LedgerCallFailedException e) {
      if (e.isTransient()) {
        log.warn(LedgerApiConstants.LOG_POSTING_TIMEOUT, postingId, e.attempts(), e.getCause().toString());
        return new Unknown(UnknownReason.LEDGER_TIMEOUT);
      }
      log.error(LedgerApiConstants.LOG_POSTING_FAILED, postingId, e);
      return new Unknown(UnknownReason.LEDGER_ERROR);
    } catch (JacksonException e) {
      log.error(LedgerApiConstants.LOG_POSTING_FAILED, postingId, e);
      return new Unknown(UnknownReason.LEDGER_ERROR);
    }
    return classify(postingId, response);
  }

  /** Keyed on the HTTP status first, then on the body's {@code code}. */
  private PostingOutcome classify(UUID postingId, LedgerHttpResponse response) {
    if (response.is(HttpStatus.OK)) {
      return read(response, PostingResponseDto.class)
          .flatMap(mapper::toPosted)
          .orElseGet(() -> unparsable(postingId, response));
    }
    if (response.is(HttpStatus.UNPROCESSABLE_CONTENT)) {
      return read(response, LedgerProblemDto.class)
          .map(mapper::toRejected)
          .orElseGet(() -> unparsable(postingId, response));
    }
    if (response.is(HttpStatus.CONFLICT)) {
      log.error(LedgerApiConstants.LOG_POSTING_CONFLICT, postingId);
      return new Unknown(UnknownReason.POSTING_CONFLICT);
    }
    log.error(LedgerApiConstants.LOG_POSTING_ERROR, postingId, response.statusValue());
    return new Unknown(UnknownReason.LEDGER_ERROR);
  }

  private PostingOutcome unparsable(UUID postingId, LedgerHttpResponse response) {
    log.error(LedgerApiConstants.LOG_POSTING_UNPARSABLE, postingId, response.statusValue());
    return new Unknown(UnknownReason.LEDGER_ERROR);
  }

  // ---- LedgerQueryPort ------------------------------------------------------------------------------------------

  @Override
  public PostingLookup lookupPosting(UUID postingId, int legCount) {
    if (legCount < LedgerApiConstants.MIN_LEGS || legCount > LedgerApiConstants.MAX_LEGS) {
      throw new IllegalArgumentException(LedgerApiConstants.MSG_INVALID_LEG_COUNT
          .formatted(LedgerApiConstants.MIN_LEGS, LedgerApiConstants.MAX_LEGS, legCount));
    }
    String wireId = mapper.toWireId(postingId);
    LedgerHttpResponse response = executeOrThrow(() -> restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path(LedgerApiConstants.POSTING_PATH)
            .queryParam(LedgerApiConstants.QUERY_LEGS, legCount)
            .build(wireId))
        .exchangeForRequiredValue(CAPTURE));
    if (!response.is(HttpStatus.OK)) {
      throw unexpected(response, LedgerApiConstants.POSTING_PATH);
    }
    PostingLookupResponseDto body = read(response, PostingLookupResponseDto.class)
        .orElseThrow(() -> unparsableException(response, LedgerApiConstants.POSTING_PATH));
    return mapper.toPostingLookup(body)
        .orElseThrow(() -> new LedgerUnavailableException(
            LedgerApiConstants.MSG_UNKNOWN_LOOKUP_STATUS.formatted(body.status(), wireId)));
  }

  @Override
  public AccountBalance balance(UUID accountId) {
    String wireId = mapper.toWireId(accountId);
    LedgerHttpResponse response = executeOrThrow(() -> restClient.get()
        .uri(LedgerApiConstants.ACCOUNT_BALANCE_PATH, wireId)
        .exchangeForRequiredValue(CAPTURE));
    if (response.is(HttpStatus.OK)) {
      return read(response, BalanceResponseDto.class)
          .map(mapper::toAccountBalance)
          .orElseThrow(() -> unparsableException(response, LedgerApiConstants.ACCOUNT_BALANCE_PATH));
    }
    if (response.is(HttpStatus.NOT_FOUND)) {
      throw new LedgerAccountNotFoundException(LedgerApiConstants.MSG_ACCOUNT_NOT_FOUND.formatted(wireId));
    }
    throw unexpected(response, LedgerApiConstants.ACCOUNT_BALANCE_PATH);
  }

  // ---- LedgerAccountsPort ---------------------------------------------------------------------------------------

  @Override
  public AccountCreation createAccount(LedgerAccount account) {
    byte[] body = writeOrThrow(() -> jsonMapper.writeValueAsBytes(mapper.toAccountRequestDto(account)));
    LedgerHttpResponse response = executeOrThrow(() -> restClient.post()
        .uri(LedgerApiConstants.ACCOUNTS_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchangeForRequiredValue(CAPTURE));
    if (response.is(HttpStatus.CREATED) || response.is(HttpStatus.OK)) {
      return mapper.toAccountCreation(response.is(HttpStatus.CREATED));
    }
    if (response.is(HttpStatus.CONFLICT)) {
      throw new LedgerConflictException(
          LedgerApiConstants.MSG_ACCOUNT_CONFLICT.formatted(mapper.toWireId(account.accountId())));
    }
    throw unexpected(response, LedgerApiConstants.ACCOUNTS_PATH);
  }

  @Override
  public void fund(UUID fundingId, UUID accountId, long amount) {
    byte[] body = writeOrThrow(
        () -> jsonMapper.writeValueAsBytes(mapper.toFundingRequestDto(fundingId, accountId, amount)));
    LedgerHttpResponse response = executeOrThrow(() -> restClient.post()
        .uri(LedgerApiConstants.FUNDINGS_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchangeForRequiredValue(CAPTURE));
    if (response.is(HttpStatus.OK)) {
      return;
    }
    if (response.is(HttpStatus.UNPROCESSABLE_CONTENT)) {
      String code = read(response, LedgerProblemDto.class).map(LedgerProblemDto::code).orElse(null);
      if (mapper.toFailureCode(code) == FailureCode.WALLET_NOT_FOUND) {
        throw new LedgerAccountNotFoundException(
            LedgerApiConstants.MSG_ACCOUNT_NOT_FOUND.formatted(mapper.toWireId(accountId)));
      }
      throw new LedgerUnavailableException(
          LedgerApiConstants.MSG_FUNDING_REJECTED.formatted(mapper.toWireId(fundingId), code));
    }
    if (response.is(HttpStatus.CONFLICT)) {
      throw new LedgerConflictException(
          LedgerApiConstants.MSG_FUNDING_CONFLICT.formatted(mapper.toWireId(fundingId)));
    }
    throw unexpected(response, LedgerApiConstants.FUNDINGS_PATH);
  }

  // ---- Retry, budget and JSON helpers ---------------------------------------------------------------------------

  /**
   * Runs {@code call} under the ledger retry template. A 503 answer becomes a retryable exception; any other answer
   * is returned. Throws {@link LedgerCallFailedException} when no answer was obtained.
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
      boolean transientFailure = RETRYABLE.test(cause) || cause instanceof LedgerBudgetExhaustedException;
      throw new LedgerCallFailedException(budget.attempts(), transientFailure, cause);
    }
  }

  private LedgerHttpResponse executeOrThrow(Supplier<LedgerHttpResponse> call) {
    try {
      return execute(call);
    } catch (LedgerCallFailedException e) {
      throw new LedgerUnavailableException(e.getMessage(), e.getCause());
    }
  }

  private <T> Optional<T> read(LedgerHttpResponse response, Class<T> type) {
    try {
      return Optional.ofNullable(jsonMapper.readValue(response.body(), type));
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  private static byte[] writeOrThrow(Supplier<byte[]> writer) {
    try {
      return writer.get();
    } catch (JacksonException e) {
      throw new LedgerUnavailableException(e.getOriginalMessage(), e);
    }
  }

  private static LedgerUnavailableException unexpected(LedgerHttpResponse response, String path) {
    return new LedgerUnavailableException(LedgerApiConstants.MSG_UNEXPECTED_STATUS.formatted(response.statusValue(), path));
  }

  private static LedgerUnavailableException unparsableException(LedgerHttpResponse response, String path) {
    return new LedgerUnavailableException(LedgerApiConstants.MSG_UNPARSABLE.formatted(response.statusValue(), path));
  }
}
