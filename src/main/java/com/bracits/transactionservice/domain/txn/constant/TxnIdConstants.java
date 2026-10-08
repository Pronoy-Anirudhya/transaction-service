package com.bracits.transactionservice.domain.txn.constant;

import com.bracits.transactionservice.domain.constant.DomainConstants;

/**
 * Bit layout of a txnId (spec 6.3): 48-bit Unix-millisecond timestamp, 72 random bits, 8 zero bits
 * (leg index).
 *
 * <pre>
 * most significant 64 bits:  [ 48 bits epoch millis ][ 16 random bits ]
 * least significant 64 bits: [ 56 random bits ][ 8 zero bits ]
 * </pre>
 */
public final class TxnIdConstants {

  /**
   * Width of the Unix-millisecond timestamp at the top of a txnId.
   */
  public static final int TIMESTAMP_BITS = 48;

  /**
   * Shift of the timestamp inside the most significant 64 bits (= 16 random bits below it).
   */
  public static final int TIMESTAMP_SHIFT = Long.SIZE - TIMESTAMP_BITS;

  /**
   * Largest epoch-millisecond value that fits in {@link #TIMESTAMP_BITS}.
   */
  public static final long MAX_EPOCH_MILLIS = (1L << TIMESTAMP_BITS) - 1;

  /**
   * Mask of the random bits in the most significant 64 bits.
   */
  public static final long MSB_RANDOM_MASK = (1L << TIMESTAMP_SHIFT) - 1;

  /**
   * Mask of the leg-index bits in the least significant 64 bits.
   */
  public static final long LEG_INDEX_MASK = (1L << DomainConstants.LEG_INDEX_BITS) - 1;

  /**
   * Smallest leg index ({@code transferId = txnId | legIndex}).
   */
  public static final int MIN_LEG_INDEX = 1;

  /**
   * Largest leg index that fits in the low zero bits.
   */
  public static final int MAX_LEG_INDEX = (int) LEG_INDEX_MASK;

  /**
   * Separator between wallet ID and Idempotency-Key in the funding-ID name (decision B11).
   */
  public static final String FUNDING_NAME_SEPARATOR = ":";

  public static final String LEG_INDEX_OUT_OF_RANGE = "Leg index must be %d..%d: %d";
  public static final String LEG_BITS_NOT_ZERO = "The low %d bits of a txnId must be zero: %s";
  public static final String EPOCH_MILLIS_OUT_OF_RANGE = "Epoch millis must be 0..%d: %d";

  private TxnIdConstants() {
  }
}
