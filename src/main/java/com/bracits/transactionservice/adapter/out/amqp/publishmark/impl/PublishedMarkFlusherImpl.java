package com.bracits.transactionservice.adapter.out.amqp.publishmark.impl;

import com.bracits.transactionservice.adapter.out.amqp.constant.AmqpConstants;
import com.bracits.transactionservice.adapter.out.amqp.publishmark.PublishedMarkBuffer;
import com.bracits.transactionservice.adapter.out.amqp.publishmark.PublishedMarkFlusher;
import com.bracits.transactionservice.config.constant.PropertyConstants;
import com.bracits.transactionservice.config.properties.EventsProperties;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link PublishedMarkFlusher}.
 */
@Component
public final class PublishedMarkFlusherImpl implements PublishedMarkFlusher {

  private static final Logger LOG = LoggerFactory.getLogger(PublishedMarkFlusherImpl.class);

  private final PublishedMarkBuffer buffer;
  private final TxnRepository txnRepository;
  private final int batchSize;
  private final ReentrantLock flushLock = new ReentrantLock();
  private final AtomicBoolean earlyFlushPending = new AtomicBoolean();

  public PublishedMarkFlusherImpl(PublishedMarkBuffer buffer, TxnRepository txnRepository,
      EventsProperties properties) {
    this.buffer = buffer;
    this.txnRepository = txnRepository;
    this.batchSize = properties.flushBatchSize();
  }

  /**
   * Called on broker ack. Never blocks: a full batch is flushed on a virtual thread.
   */
  @Override
  public void onAck(UUID txnId) {
    if (buffer.add(txnId) >= batchSize) {
      requestEarlyFlush();
    }
  }

  @Scheduled(fixedDelayString = PropertyConstants.EVENTS_FLUSH_INTERVAL_PLACEHOLDER)
  @Override
  public void scheduledFlush() {
    flush();
  }

  /**
   * Drains the buffer in statements of at most {@code batchSize} IDs. Skips (returns 0) if another
   * flush is running; that flush drains the buffer anyway.
   *
   * @return number of IDs handed to the repository (written or dropped)
   */
  @Override
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

  /**
   * Final flush on shutdown; waits for a running flush instead of skipping.
   */
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
