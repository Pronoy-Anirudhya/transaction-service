package com.bracits.transactionservice.domain.txn;

import com.bracits.transactionservice.domain.DomainConstants;
import com.bracits.transactionservice.domain.event.EventIds;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TxnIdsTest {

  private static final long MILLIS = 1_791_417_600_123L; // 2026-10-08T00:00:00.123Z
  private static final UUID TXN_ID = new UUID((MILLIS << 16) | 0xabcdL, 0x1122_3344_5566_7700L);

  @Test
  void legTransferIdSetsLowByte() {
    assertThat(TxnIds.legTransferId(TXN_ID, 1))
        .isEqualTo(new UUID(TXN_ID.getMostSignificantBits(), 0x1122_3344_5566_7701L));
    assertThat(TxnIds.legTransferId(TXN_ID, 4))
        .isEqualTo(new UUID(TXN_ID.getMostSignificantBits(), 0x1122_3344_5566_7704L));
    assertThat(TxnIds.legTransferId(TXN_ID, 255).getLeastSignificantBits() & 0xff).isEqualTo(255);
  }

  @Test
  void legTransferIdRejectsBadInput() {
    assertThatThrownBy(() -> TxnIds.legTransferId(TXN_ID, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TxnIds.legTransferId(TXN_ID, 256)).isInstanceOf(IllegalArgumentException.class);
    UUID notATxnId = new UUID(TXN_ID.getMostSignificantBits(), TXN_ID.getLeastSignificantBits() | 1);
    assertThatThrownBy(() -> TxnIds.legTransferId(notATxnId, 1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void epochMillisIsTopFortyEightBits() {
    assertThat(TxnIds.epochMillis(TXN_ID)).isEqualTo(MILLIS);
  }

  @Test
  void lowerBoundIsSmallestIdOfTheMillisecond() {
    UUID bound = TxnIds.lowerBound(Instant.ofEpochMilli(MILLIS));

    assertThat(bound).isEqualTo(new UUID(MILLIS << 16, 0L));
    assertThat(TxnIds.epochMillis(bound)).isEqualTo(MILLIS);
    assertThat(TxnIdOrder.compareUnsigned(bound, TXN_ID)).isNegative();
    assertThat(TxnIdOrder.compareUnsigned(TxnIds.lowerBound(Instant.ofEpochMilli(MILLIS + 1)), TXN_ID)).isPositive();
  }

  @Test
  void lowerBoundIgnoresSubMillisecondPrecision() {
    assertThat(TxnIds.lowerBound(Instant.ofEpochMilli(MILLIS).plusNanos(999_999)))
        .isEqualTo(TxnIds.lowerBound(Instant.ofEpochMilli(MILLIS)));
  }

  @Test
  void lowerBoundRejectsOutOfRangeInstants() {
    assertThatThrownBy(() -> TxnIds.lowerBound(Instant.ofEpochMilli(-1))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TxnIds.lowerBound(Instant.ofEpochMilli(1L << 48)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fundingIdIsDeterministicWithZeroLowByte() {
    UUID id = TxnIds.fundingId(42, "key-1");

    assertThat(TxnIds.fundingId(42, "key-1")).isEqualTo(id);
    assertThat(id.getLeastSignificantBits() & 0xff).isZero();
    assertThat(TxnIds.fundingId(42, "key-2")).isNotEqualTo(id);
    assertThat(TxnIds.fundingId(43, "key-1")).isNotEqualTo(id);
  }

  @Test
  void fundingIdIsUuidV5OfWalletAndKey() {
    UUID v5 = EventIds.uuidV5(DomainConstants.FUNDING_ID_NAMESPACE, "42:key-1");

    assertThat(TxnIds.fundingId(42, "key-1"))
        .isEqualTo(new UUID(v5.getMostSignificantBits(), v5.getLeastSignificantBits() & ~0xffL));
  }
}
