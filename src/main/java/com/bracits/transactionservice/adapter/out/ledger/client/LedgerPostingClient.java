package com.bracits.transactionservice.adapter.out.ledger.client;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;
import com.bracits.transactionservice.adapter.out.ledger.dto.LedgerProblemDto;
import com.bracits.transactionservice.adapter.out.ledger.dto.PostingResponseDto;
import com.bracits.transactionservice.adapter.out.ledger.exception.LedgerCallFailedException;
import com.bracits.transactionservice.adapter.out.ledger.http.LedgerHttpExecutor;
import com.bracits.transactionservice.adapter.out.ledger.mapper.LedgerDtoMapper;
import com.bracits.transactionservice.adapter.out.ledger.model.LedgerHttpResponse;
import com.bracits.transactionservice.config.constant.MetricConstants;
import com.bracits.transactionservice.config.constant.PropertyConstants;
import com.bracits.transactionservice.domain.ledger.enums.UnknownReason;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome.Unknown;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import com.bracits.transactionservice.port.out.client.LedgerPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ProxyType;
import org.springframework.context.annotation.Proxyable;
import org.springframework.http.HttpStatus;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * {@link LedgerPort} over HTTP: {@code POST /internal/v1/postings}. Ledger answers never throw: 200
 * → Posted, 422 → Rejected, everything else (409, 500, unparsable, retries or budget exhausted) →
 * Unknown.
 *
 * <p>Deliberately <em>not</em> final: {@code @ConcurrencyLimit} on {@link #post} needs a
 * class-based (CGLIB) proxy. In Spring Framework 7.0.9 the concurrency interceptor reads the
 * annotation from the invoked method, which on a JDK interface proxy is {@link LedgerPort#post}
 * (unannotated), so the call would fail with "No @ConcurrencyLimit annotation found".
 * {@link Proxyable} pins the class proxy regardless of {@code spring.aop.proxy-target-class}.
 */
@Component
@Proxyable(ProxyType.TARGET_CLASS)
public class LedgerPostingClient implements LedgerPort {

  private static final Logger LOG = LoggerFactory.getLogger(LedgerPostingClient.class);

  private final LedgerHttpExecutor http;
  private final LedgerDtoMapper mapper;
  private final MeterRegistry meterRegistry;

  public LedgerPostingClient(LedgerHttpExecutor http, LedgerDtoMapper mapper,
      MeterRegistry meterRegistry) {
    this.http = http;
    this.mapper = mapper;
    this.meterRegistry = meterRegistry;
  }

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
      byte[] body = http.toJson(mapper.toPostingRequestDto(request));
      response = http.postJson(LedgerApiConstants.POSTINGS_PATH, body);
    } catch (LedgerCallFailedException e) {
      if (e.isTransient()) {
        LOG.warn(LedgerApiConstants.LOG_POSTING_TIMEOUT, postingId, e.attempts(),
            e.getCause().toString());
        return new Unknown(UnknownReason.LEDGER_TIMEOUT);
      }

      LOG.error(LedgerApiConstants.LOG_POSTING_FAILED, postingId, e);
      return new Unknown(UnknownReason.LEDGER_ERROR);
    } catch (JacksonException e) {
      LOG.error(LedgerApiConstants.LOG_POSTING_FAILED, postingId, e);
      return new Unknown(UnknownReason.LEDGER_ERROR);
    }

    return classify(postingId, response);
  }

  /**
   * Keyed on the HTTP status first, then on the body's {@code code}.
   */
  private PostingOutcome classify(UUID postingId, LedgerHttpResponse response) {
    if (response.is(HttpStatus.OK)) {
      return http.read(response, PostingResponseDto.class)
          .flatMap(mapper::toPosted)
          .orElseGet(() -> unparsable(postingId, response));
    }

    if (response.is(HttpStatus.UNPROCESSABLE_CONTENT)) {
      return http.read(response, LedgerProblemDto.class)
          .map(mapper::toRejected)
          .orElseGet(() -> unparsable(postingId, response));
    }

    if (response.is(HttpStatus.CONFLICT)) {
      LOG.error(LedgerApiConstants.LOG_POSTING_CONFLICT, postingId);
      return new Unknown(UnknownReason.POSTING_CONFLICT);
    }

    LOG.error(LedgerApiConstants.LOG_POSTING_ERROR, postingId, response.statusValue());
    return new Unknown(UnknownReason.LEDGER_ERROR);
  }

  private PostingOutcome unparsable(UUID postingId, LedgerHttpResponse response) {
    LOG.error(LedgerApiConstants.LOG_POSTING_UNPARSABLE, postingId, response.statusValue());
    return new Unknown(UnknownReason.LEDGER_ERROR);
  }
}
