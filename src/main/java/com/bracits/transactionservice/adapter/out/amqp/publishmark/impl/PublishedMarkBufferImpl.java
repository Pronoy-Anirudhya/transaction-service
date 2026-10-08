package com.bracits.transactionservice.adapter.out.amqp.publishmark.impl;

import com.bracits.transactionservice.adapter.out.amqp.publishmark.PublishedMarkBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link PublishedMarkBuffer}.
 */
@Component
public final class PublishedMarkBufferImpl implements PublishedMarkBuffer {

  private final ConcurrentLinkedQueue<UUID> ids = new ConcurrentLinkedQueue<>();
  private final AtomicInteger size = new AtomicInteger();

  /**
   * Adds {@code txnId}; returns the (approximate) number of buffered IDs after the add.
   */
  @Override
  public int add(UUID txnId) {
    ids.add(txnId);
    return size.incrementAndGet();
  }

  /**
   * Removes and returns up to {@code max} IDs, oldest first.
   */
  @Override
  public List<UUID> drain(int max) {
    List<UUID> batch = new ArrayList<>(Math.min(max, Math.max(size.get(), 0)));
    UUID id;
    while (batch.size() < max && (id = ids.poll()) != null) {
      batch.add(id);
      size.decrementAndGet();
    }

    return batch;
  }

  @Override
  public int size() {
    return Math.max(size.get(), 0);
  }

  @Override
  public boolean isEmpty() {
    return ids.isEmpty();
  }
}
