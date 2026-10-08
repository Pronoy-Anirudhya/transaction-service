package com.bracits.transactionservice.adapter.out.amqp.executor;


/**
 * One virtual thread per event send (P1, P11). Deliberately NOT a
 * {@link java.util.concurrent.Executor}: a bean of that type would make Spring Boot back off its
 * {@code applicationTaskExecutor}.
 */
public interface EventSendExecutor extends AutoCloseable {

  /**
   * Runs {@code task} on a new virtual thread.
   *
   * @throws java.util.concurrent.RejectedExecutionException after {@link #close()}
   */
  void execute(Runnable task);

  /**
   * Stops accepting tasks; never throws a checked exception.
   */
  @Override
  void close();
}
