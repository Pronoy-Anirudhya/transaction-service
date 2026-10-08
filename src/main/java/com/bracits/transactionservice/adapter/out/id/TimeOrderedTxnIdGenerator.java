package com.bracits.transactionservice.adapter.out.id;

import com.bracits.transactionservice.domain.DomainConstants;
import com.bracits.transactionservice.domain.txn.TxnIdConstants;
import com.bracits.transactionservice.port.out.TxnIdGenerator;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Time-ordered txnIds (spec 6.3, P12): 48-bit epoch millis, 72 random bits from {@link ThreadLocalRandom},
 * 8 zero bits. Lock-free; no database sequence, no UUIDv4. IDs of later milliseconds sort later, which keeps
 * B-tree and LSM inserts sequential.
 */
@Component
public final class TimeOrderedTxnIdGenerator implements TxnIdGenerator {

  private static final int TIMESTAMP_SHIFT = TxnIdConstants.TIMESTAMP_SHIFT;
  private static final long MSB_RANDOM_MASK = TxnIdConstants.MSB_RANDOM_MASK;
  private static final int LEG_INDEX_BITS = DomainConstants.LEG_INDEX_BITS;

  private final Clock clock;

  public TimeOrderedTxnIdGenerator(Clock clock) {
    this.clock = clock;
  }

  @Override
  public UUID next() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    long msb = (clock.millis() << TIMESTAMP_SHIFT) | (random.nextLong() & MSB_RANDOM_MASK);
    // 56 random bits on top, 8 zero bits for the leg index.
    long lsb = random.nextLong() << LEG_INDEX_BITS;
    return new UUID(msb, lsb);
  }
}
