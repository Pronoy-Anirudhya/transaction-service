package com.bracits.transactionservice.adapter.out.amqp.publishmark;

import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.util.UUID;

/**
 * Writes the batched {@code event_published_at} mark (spec 9 rule 4, P11): every
 * {@code poc.events.flush-interval} (100 ms), or as soon as {@code poc.events.flush-batch-size}
 * (500) IDs are buffered, in statements of at most that many IDs
 * ({@link TxnRepository#markEventsPublished}, {@code synchronous_commit = off}). A failed write is
 * logged and dropped: a missing mark only causes a harmless republish.
 */
public interface PublishedMarkFlusher {

  /**
   * Called on broker ack. Never blocks: a full batch is flushed on a virtual thread.
   */
  void onAck(UUID txnId);

  void scheduledFlush();

  /**
   * Drains the buffer in statements of at most {@code batchSize} IDs. Skips (returns 0) if another
   * flush is running; that flush drains the buffer anyway.
   *
   * @return number of IDs handed to the repository (written or dropped)
   */
  int flush();
}
