package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.config.constant.LogConstants;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import com.bracits.transactionservice.port.out.client.LedgerPort;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.MDC;

/**
 * A ledger that answers from a queue of scripted steps (an outcome, an exception or a custom
 * answer), then from the fallback answer if one is set. A call with nothing scripted fails the
 * test. Records every request and the MDC {@code txnId} seen during the call.
 */
public final class ScriptedLedgerPort implements LedgerPort {

  /**
   * One scripted answer.
   */
  @FunctionalInterface
  public interface Step {

    PostingOutcome answer(PostingRequest request);
  }

  private final Deque<Step> script = new ConcurrentLinkedDeque<>();
  private final List<PostingRequest> requests = new CopyOnWriteArrayList<>();
  private final List<Optional<String>> mdcTxnIds = new CopyOnWriteArrayList<>();
  private volatile Step fallback;

  public ScriptedLedgerPort thenReturn(PostingOutcome outcome) {
    script.add(request -> outcome);
    return this;
  }

  public ScriptedLedgerPort thenThrow(RuntimeException failure) {
    script.add(request -> {
      throw failure;
    });
    return this;
  }

  public ScriptedLedgerPort thenAnswer(Step step) {
    script.add(step);
    return this;
  }

  /**
   * Answer used once the script is exhausted (useful for concurrent callers).
   */
  public ScriptedLedgerPort otherwise(Step step) {
    fallback = step;
    return this;
  }

  @Override
  public PostingOutcome post(PostingRequest request) {
    requests.add(request);
    mdcTxnIds.add(Optional.ofNullable(MDC.get(LogConstants.MDC_TXN_ID)));

    Step step = script.poll();
    if (step == null) {
      step = fallback;
    }

    if (step == null) {
      throw new AssertionError("unexpected ledger call for posting " + request.postingId());
    }

    return step.answer(request);
  }

  public List<PostingRequest> requests() {
    return List.copyOf(requests);
  }

  public List<Optional<String>> mdcTxnIds() {
    return List.copyOf(mdcTxnIds);
  }
}
