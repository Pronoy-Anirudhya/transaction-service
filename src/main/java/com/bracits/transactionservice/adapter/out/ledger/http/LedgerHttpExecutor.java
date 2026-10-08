package com.bracits.transactionservice.adapter.out.ledger.http;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;
import com.bracits.transactionservice.adapter.out.ledger.exception.LedgerCallFailedException;
import com.bracits.transactionservice.adapter.out.ledger.model.LedgerHttpResponse;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import java.net.URI;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.web.util.UriBuilder;
import tools.jackson.core.JacksonException;

/**
 * Shared HTTP plumbing of the ledger clients (spec 8.3): every call runs under the ledger retry
 * template, is retried only on 503 ({@code LEDGER_TIMEOUT} or {@code OVERLOADED}) / I/O error /
 * timeout, at most {@code maxRetries} times, within the total budget, and a retry starts only while
 * at least one read timeout of budget is left (decision B7). Each retry resends the identical body
 * bytes. Answers are returned raw so each client classifies them by HTTP status first, then by the
 * problem {@code code}. Bodies are never logged.
 *
 * <p>{@code Retry-After: 1} on a 503 is deliberately not slept inside the request: the spec 8.3
 * backoff (50 ms ×2 with jitter) and the 3 s budget take precedence, since a 1 s wait would leave
 * no room for a full read timeout (decision L2).
 */
public interface LedgerHttpExecutor {

  /**
   * POSTs JSON bytes. Throws {@link LedgerCallFailedException} when no answer was obtained.
   */
  LedgerHttpResponse postJson(String path, byte[] body);

  /**
   * As {@link #postJson}, but a missing answer becomes {@link LedgerUnavailableException}.
   */
  LedgerHttpResponse postJsonOrThrow(String path, byte[] body);

  /**
   * GETs {@code uri}; a missing answer becomes {@link LedgerUnavailableException}.
   */
  LedgerHttpResponse getOrThrow(Function<UriBuilder, URI> uri);

  /**
   * Serialises a wire DTO; {@link JacksonException} propagates to the caller.
   */
  byte[] toJson(Object dto);

  /**
   * As {@link #toJson}, but a serialisation failure becomes {@link LedgerUnavailableException}.
   */
  byte[] toJsonOrThrow(Object dto);

  /**
   * Parses a body; empty when it is not valid JSON of that shape.
   */
  <T> Optional<T> read(LedgerHttpResponse response, Class<T> type);

  static LedgerUnavailableException unexpectedStatus(LedgerHttpResponse response,
      String path) {
    return new LedgerUnavailableException(
        LedgerApiConstants.MSG_UNEXPECTED_STATUS.formatted(response.statusValue(), path));
  }

  static LedgerUnavailableException unparsableBody(LedgerHttpResponse response,
      String path) {
    return new LedgerUnavailableException(
        LedgerApiConstants.MSG_UNPARSABLE.formatted(response.statusValue(), path));
  }
}
