package com.bracits.transactionservice.adapter.out.amqp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One virtual thread per event send (P1, P11). Deliberately NOT a {@link java.util.concurrent.Executor}: a bean of that
 * type would make Spring Boot back off its {@code applicationTaskExecutor}.
 */
public final class EventSendExecutor implements AutoCloseable {

  private final ExecutorService delegate;

  public EventSendExecutor(String threadNamePrefix) {
    this.delegate = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(threadNamePrefix, 0).factory());
  }

  /**
   * Runs {@code task} on a new virtual thread.
   *
   * @throws java.util.concurrent.RejectedExecutionException after {@link #close()}
   */
  public void execute(Runnable task) {
    delegate.execute(task);
  }

  /** Waits for in-flight sends (they are short: confirms are handled asynchronously), then stops. */
  @Override
  public void close() {
    delegate.close();
  }
}
