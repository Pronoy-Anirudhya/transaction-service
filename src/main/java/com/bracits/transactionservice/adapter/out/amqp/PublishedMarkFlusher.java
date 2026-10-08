package com.bracits.transactionservice.adapter.out.amqp;

import com.bracits.transactionservice.config.EventsProperties;
import com.bracits.transactionservice.config.PropertyConstants;
import com.bracits.transactionservice.port.out.TxnRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writes the batched {@code event_published_at} mark (spec 9 rule 4, P11): every {@code poc.events.flush-interval}
 * (100 ms), or as soon as {@code poc.events.flush-batch-size} (500) IDs are buffered, in statements of at most that
 * many IDs ({@link TxnRepository#markEventsPublished}, {@code synchronous_commit = off}). A failed write is logged and
 * dropped: a missing mark only causes a harmless republish.
 */
@Component
public final class PublishedMarkFlusher {

  private static final Logger LOG = LoggerFactory.getLogger(PublishedMarkFlusher.class);

  private final PublishedMarkBuffer buffer;
  private final TxnRepository txnRepository;
  private final int batchSize;
  private final ReentrantLock flushLock = new ReentrantLock();
  private final AtomicBoolean earlyFlushPending = new AtomicBoolean();

  public PublishedMarkFlusher(PublishedMarkBuffer buffer, TxnRepository txnRepository, EventsProperties properties) {
    this.buffer = buffer;
    this.txnRepository = txnRepository;
    this.batchSize = properties.flushBatchSize();
  }

  /** Called on broker ack. Never blocks: a full batch is flushed on a virtual thread. */
  public void onAck(UUID txnId) {
    if (buffer.add(txnId) >= batchSize) {
      requestEarlyFlush();
    }
  }

  @Scheduled(fixedDelayString = PropertyConstants.EVENTS_FLUSH_INTERVAL_PLACEHOLDER)
  public void scheduledFlush() {
    flush();
  }

  /**
   * Drains the buffer in statements of at most {@code batchSize} IDs. Skips (returns 0) if another flush is running;
   * that flush drains the buffer anyway.
   *
   * @return number of IDs handed to the repository (written or dropped)
   */
  public int flush() {
    if (!flushLock.tryLock()) {
      return 0;
    }
    try {
      return drainAll();
    } finally {
      flushLock.unlock();
    }
  }

  /** Final flush on shutdown; waits for a running flush instead of skipping. */
  @PreDestroy
  public void flushOnShutdown() {
    flushLock.lock();
    try {
      drainAll();
    } finally {
      flushLock.unlock();
    }
  }

  private void requestEarlyFlush() {
    if (earlyFlushPending.compareAndSet(false, true)) {
      Thread.ofVirtual().name(AmqpConstants.MARK_FLUSH_THREAD_NAME).start(() -> {
        try {
          flush();
        } finally {
          earlyFlushPending.set(false);
        }
      });
    }
  }

  private int drainAll() {
    int handled = 0;
    List<UUID> batch = buffer.drain(batchSize);
    while (!batch.isEmpty()) {
      write(batch);
      handled += batch.size();
      batch = buffer.drain(batchSize);
    }
    return handled;
  }

  private void write(List<UUID> batch) {
    try {
      txnRepository.markEventsPublished(batch);
    } catch (RuntimeException e) {
      LOG.warn(AmqpConstants.LOG_MARK_FLUSH_FAILED, batch.size(), e.toString());
    }
  }
}
