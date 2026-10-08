package com.bracits.transactionservice.adapter.out.ledger.retry;

import com.bracits.transactionservice.adapter.out.ledger.exception.LedgerTransientStatusException;
import java.util.function.Predicate;
import org.springframework.web.client.ResourceAccessException;

/**
 * Retry only when the outcome is unknown and a resend is safe (spec 8.3): a 503 answer
 * ({@link LedgerTransientStatusException}) or an I/O error, connect timeout or read timeout, which
 * {@code RestClient} reports as {@link ResourceAccessException}. Everything else (422, 409, 500,
 * budget exhausted, bugs) is final. Matches the thrown exception itself only, not its causes.
 */
public final class LedgerRetryPredicate implements Predicate<Throwable> {

  @Override
  public boolean test(Throwable throwable) {
    return throwable instanceof LedgerTransientStatusException
        || throwable instanceof ResourceAccessException;
  }
}
