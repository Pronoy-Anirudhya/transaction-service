package com.bracits.transactionservice.adapter.out.amqp;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory, lock-free buffer of txnIds whose event the broker acked (spec 9 rule 4). Losing its content (crash) only
 * causes a harmless republish, so it is deliberately not durable.
 */
@Component
public final class PublishedMarkBuffer {

  private final ConcurrentLinkedQueue<UUID> ids = new ConcurrentLinkedQueue<>();
  private final AtomicInteger size = new AtomicInteger();

  /** Adds {@code txnId}; returns the (approximate) number of buffered IDs after the add. */
  public int add(UUID txnId) {
    ids.add(txnId);
    return size.incrementAndGet();
  }

  /** Removes and returns up to {@code max} IDs, oldest first. */
  public List<UUID> drain(int max) {
    List<UUID> batch = new ArrayList<>(Math.min(max, Math.max(size.get(), 0)));
    UUID id;
    while (batch.size() < max && (id = ids.poll()) != null) {
      batch.add(id);
      size.decrementAndGet();
    }
    return batch;
  }

  public int size() {
    return Math.max(size.get(), 0);
  }

  public boolean isEmpty() {
    return ids.isEmpty();
  }
}
