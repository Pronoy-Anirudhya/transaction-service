package com.bracits.transactionservice.adapter.out.amqp.executor.impl;

import com.bracits.transactionservice.adapter.out.amqp.executor.EventSendExecutor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Default implementation of {@link EventSendExecutor}.
 */
public final class EventSendExecutorImpl implements EventSendExecutor {

  private final ExecutorService delegate;

  public EventSendExecutorImpl(String threadNamePrefix) {
    this.delegate = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name(threadNamePrefix, 0).factory());
  }

  /**
   * Runs {@code task} on a new virtual thread.
   *
   * @throws java.util.concurrent.RejectedExecutionException after {@link #close()}
   */
  @Override
  public void execute(Runnable task) {
    delegate.execute(task);
  }

  /**
   * Waits for in-flight sends (they are short: confirms are handled asynchronously), then stops.
   */
  @Override
  public void close() {
    delegate.close();
  }
}
