package com.bracits.transactionservice.adapter.out.id;

import com.bracits.transactionservice.domain.txn.TxnIdOrder;
import com.bracits.transactionservice.domain.txn.TxnIds;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TimeOrderedTxnIdGeneratorTest {

  private static final long MILLIS = 1_791_417_600_123L; // 2026-10-08T00:00:00.123Z
  private static final int SAMPLES = 10_000;

  @Test
  void lowByteIsZeroAndTimestampIsClockMillis() {
    TimeOrderedTxnIdGenerator generator =
        new TimeOrderedTxnIdGenerator(Clock.fixed(Instant.ofEpochMilli(MILLIS), ZoneOffset.UTC));

    for (int i = 0; i < SAMPLES; i++) {
      UUID id = generator.next();
      assertThat(id.getLeastSignificantBits() & 0xff).isZero();
      assertThat(TxnIds.epochMillis(id)).isEqualTo(MILLIS);
      assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(MILLIS);
    }
  }

  @Test
  void idsAreUniqueWithinOneMillisecond() {
    TimeOrderedTxnIdGenerator generator =
        new TimeOrderedTxnIdGenerator(Clock.fixed(Instant.ofEpochMilli(MILLIS), ZoneOffset.UTC));
    Set<UUID> ids = new HashSet<>();

    for (int i = 0; i < SAMPLES; i++) {
      ids.add(generator.next());
    }

    assertThat(ids).hasSize(SAMPLES);
  }

  @Test
  void randomBitsVary() {
    TimeOrderedTxnIdGenerator generator =
        new TimeOrderedTxnIdGenerator(Clock.fixed(Instant.ofEpochMilli(MILLIS), ZoneOffset.UTC));
    long msbRandomUnion = 0;
    long lsbUnion = 0;

    for (int i = 0; i < SAMPLES; i++) {
      UUID id = generator.next();
      msbRandomUnion |= id.getMostSignificantBits() & 0xffffL;
      lsbUnion |= id.getLeastSignificantBits();
    }

    // All 16 + 56 random bit positions get used; the 8 leg-index bits never do.
    assertThat(msbRandomUnion).isEqualTo(0xffffL);
    assertThat(lsbUnion).isEqualTo(0xffff_ffff_ffff_ff00L);
  }

  @Test
  void laterMillisecondsSortLater() {
    AtomicLong now = new AtomicLong(MILLIS);
    Clock ticking = new Clock() {
      @Override
      public ZoneOffset getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return Instant.ofEpochMilli(now.getAndIncrement());
      }
    };
    TimeOrderedTxnIdGenerator generator = new TimeOrderedTxnIdGenerator(ticking);
    List<UUID> ids = new ArrayList<>();

    for (int i = 0; i < 1_000; i++) {
      ids.add(generator.next());
    }

    assertThat(ids).isSortedAccordingTo(TxnIdOrder::compareUnsigned);
    assertThat(ids).isSortedAccordingTo(UUID::compareTo);
    for (int i = 1; i < ids.size(); i++) {
      UUID previous = ids.get(i - 1);
      UUID current = ids.get(i);
      assertThat(TxnIdOrder.compareUnsigned(TxnIds.lowerBound(Instant.ofEpochMilli(TxnIds.epochMillis(current))),
          previous)).isPositive();
    }
  }

  @Test
  void legIdsOfGeneratedIdKeepTheTxnPrefix() {
    UUID txnId = new TimeOrderedTxnIdGenerator(Clock.fixed(Instant.ofEpochMilli(MILLIS), ZoneOffset.UTC)).next();

    for (int leg = 1; leg <= 4; leg++) {
      UUID transferId = TxnIds.legTransferId(txnId, leg);
      assertThat(transferId.getMostSignificantBits()).isEqualTo(txnId.getMostSignificantBits());
      assertThat(transferId.getLeastSignificantBits() & ~0xffL).isEqualTo(txnId.getLeastSignificantBits());
      assertThat(transferId.getLeastSignificantBits() & 0xff).isEqualTo(leg);
    }
  }
}
