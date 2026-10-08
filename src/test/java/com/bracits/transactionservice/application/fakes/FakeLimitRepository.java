package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import com.bracits.transactionservice.port.out.repository.LimitRepository;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configurable conditional limit update; records every reservation and every rollback of its
 * transaction.
 */
public final class FakeLimitRepository implements LimitRepository {

  private volatile boolean accept = true;
  private final List<LimitReservation> calls = new CopyOnWriteArrayList<>();
  private final AtomicInteger rollbacks = new AtomicInteger();

  public void accept() {
    accept = true;
  }

  public void reject() {
    accept = false;
  }

  @Override
  public boolean reserve(LimitReservation reservation) {
    calls.add(reservation);
    return accept;
  }

  /**
   * Called by {@link FakeTransactionOperations} when the surrounding transaction rolls back.
   */
  void rolledBack() {
    rollbacks.incrementAndGet();
  }

  public List<LimitReservation> calls() {
    return List.copyOf(calls);
  }

  public int rollbacks() {
    return rollbacks.get();
  }
}
