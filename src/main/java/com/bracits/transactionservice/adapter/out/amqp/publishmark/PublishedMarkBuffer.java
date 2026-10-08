package com.bracits.transactionservice.adapter.out.amqp.publishmark;

import java.util.List;
import java.util.UUID;

/**
 * In-memory, lock-free buffer of txnIds whose event the broker acked (spec 9 rule 4). Losing its
 * content (crash) only causes a harmless republish, so it is deliberately not durable.
 */
public interface PublishedMarkBuffer {

  /**
   * Adds {@code txnId}; returns the (approximate) number of buffered IDs after the add.
   */
  int add(UUID txnId);

  /**
   * Removes and returns up to {@code max} IDs, oldest first.
   */
  List<UUID> drain(int max);

  int size();

  boolean isEmpty();
}
