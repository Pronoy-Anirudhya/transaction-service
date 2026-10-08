package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.port.out.TxnIdGenerator;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Time-ordered txnIds in the spec 6.3 shape (clock millis on top, low 8 bits zero), strictly increasing. */
public final class SequentialTxnIds implements TxnIdGenerator {

  private static final int TIMESTAMP_SHIFT = 16;
  private static final int LEG_BITS = 8;

  private final Clock clock;
  private final AtomicLong sequence = new AtomicLong();
  private final List<UUID> issued = new CopyOnWriteArrayList<>();

  public SequentialTxnIds(Clock clock) {
    this.clock = clock;
  }

  @Override
  public UUID next() {
    long seq = sequence.incrementAndGet();
    UUID id = new UUID((clock.millis() << TIMESTAMP_SHIFT) | (seq & 0xFFFF), seq << LEG_BITS);
    issued.add(id);
    return id;
  }

  public List<UUID> issued() {
    return List.copyOf(issued);
  }
}
