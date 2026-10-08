package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Runs the callback with a {@link SimpleTransactionStatus}; when the callback sets rollback-only
 * (or throws), the in-memory inserts made inside it are undone and the limit repository is told
 * about the rollback.
 */
public final class FakeTransactionOperations implements TransactionOperations {

  private final InMemoryTxnRepository txns;
  private final FakeLimitRepository limits;
  private final AtomicInteger commits = new AtomicInteger();
  private final AtomicInteger rollbacks = new AtomicInteger();

  public FakeTransactionOperations(InMemoryTxnRepository txns, FakeLimitRepository limits) {
    this.txns = txns;
    this.limits = limits;
  }

  @Override
  public <T> T execute(TransactionCallback<T> action) {
    Map<UUID, SendMoneyTxn> snapshot = txns.snapshot();
    SimpleTransactionStatus status = new SimpleTransactionStatus(true);
    T result;

    try {
      result = action.doInTransaction(status);
    } catch (RuntimeException | Error e) {
      rollback(snapshot);
      throw e;
    }

    if (status.isRollbackOnly()) {
      rollback(snapshot);
    } else {
      commits.incrementAndGet();
    }

    return result;
  }

  private void rollback(Map<UUID, SendMoneyTxn> snapshot) {
    txns.restore(snapshot);
    limits.rolledBack();
    rollbacks.incrementAndGet();
  }

  public int commits() {
    return commits.get();
  }

  public int rollbacks() {
    return rollbacks.get();
  }
}
