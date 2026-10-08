package com.bracits.transactionservice.domain.txn;

import com.bracits.transactionservice.domain.DomainConstants;
import com.bracits.transactionservice.domain.event.EventIds;

import java.time.Instant;
import java.util.UUID;

/**
 * The txnId format (spec 6.3) and the IDs derived from it. Layout in {@link TxnIdConstants}.
 * The generator lives behind {@code port.out.TxnIdGenerator}; this class only reads and derives.
 */
public final class TxnIds {

  private TxnIds() {
  }

  /** Transfer ID of leg {@code legIndex} (1-based): {@code txnId | legIndex}. Deterministic, so retries are idempotent. */
  public static UUID legTransferId(UUID txnId, int legIndex) {
    if (legIndex < TxnIdConstants.MIN_LEG_INDEX || legIndex > TxnIdConstants.MAX_LEG_INDEX) {
      throw new IllegalArgumentException(TxnIdConstants.LEG_INDEX_OUT_OF_RANGE.formatted(
          TxnIdConstants.MIN_LEG_INDEX, TxnIdConstants.MAX_LEG_INDEX, legIndex));
    }
    long lsb = txnId.getLeastSignificantBits();
    if ((lsb & TxnIdConstants.LEG_INDEX_MASK) != 0) {
      throw new IllegalArgumentException(TxnIdConstants.LEG_BITS_NOT_ZERO.formatted(DomainConstants.LEG_INDEX_BITS, txnId));
    }
    return new UUID(txnId.getMostSignificantBits(), lsb | legIndex);
  }

  /** Unix-millisecond timestamp in the top 48 bits. */
  public static long epochMillis(UUID txnId) {
    return txnId.getMostSignificantBits() >>> TxnIdConstants.TIMESTAMP_SHIFT;
  }

  /**
   * Smallest txnId of the millisecond of {@code instant} (all non-timestamp bits zero). Use as an inclusive lower
   * bound, and the bound of the end instant as the exclusive upper bound, for {@code [from, to)} range scans.
   */
  public static UUID lowerBound(Instant instant) {
    long millis = instant.toEpochMilli();
    if (millis < 0 || millis > TxnIdConstants.MAX_EPOCH_MILLIS) {
      throw new IllegalArgumentException(
          TxnIdConstants.EPOCH_MILLIS_OUT_OF_RANGE.formatted(TxnIdConstants.MAX_EPOCH_MILLIS, millis));
    }
    return new UUID(millis << TxnIdConstants.TIMESTAMP_SHIFT, 0L);
  }

  /**
   * Deterministic funding transfer ID (decision B11): UUIDv5(funding namespace, walletId + ":" + Idempotency-Key)
   * with the low 8 bits cleared, so it has the same shape as a txnId.
   */
  public static UUID fundingId(long walletId, String idempotencyKey) {
    UUID id = EventIds.uuidV5(DomainConstants.FUNDING_ID_NAMESPACE,
        walletId + TxnIdConstants.FUNDING_NAME_SEPARATOR + idempotencyKey);
    return new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits() & ~TxnIdConstants.LEG_INDEX_MASK);
  }
}
