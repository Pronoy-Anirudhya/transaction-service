package com.bracits.transactionservice.application.util;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs one task per item on its own virtual thread (P1), with at most {@code parallelism} running
 * at a time, and waits for all of them. Used by the repair worker and reconciliation to bound their
 * concurrent ledger calls.
 */
public final class BoundedParallel {

  private BoundedParallel() {
  }

  /**
   * Applies {@code task} to every item; results are returned in item order. A task's exception is
   * rethrown.
   */
  public static <T, R> List<R> map(List<T> items, int parallelism, Function<T, R> task) {
    Semaphore permits = new Semaphore(parallelism);

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<R>> futures = items.stream()
          .map(item -> executor.submit(() -> withPermit(permits, () -> task.apply(item))))
          .toList();

      return futures.stream().map(BoundedParallel::await).toList();
    }
  }

  /**
   * Runs {@code task} for every item; the caller handles each task's errors.
   */
  public static <T> void forEach(List<T> items, int parallelism, Consumer<T> task) {
    map(items, parallelism, item -> {
      task.accept(item);
      return Boolean.TRUE;
    });
  }

  private static <R> R withPermit(Semaphore permits, Callable<R> task) throws Exception {
    permits.acquire();
    try {
      return task.call();
    } finally {
      permits.release();
    }
  }

  private static <R> R await(Future<R> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getCause());
    }
  }
}
