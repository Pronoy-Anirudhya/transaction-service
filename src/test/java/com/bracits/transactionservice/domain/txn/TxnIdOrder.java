package com.bracits.transactionservice.domain.txn;

import java.util.UUID;

/** Unsigned 128-bit order of UUIDs, as PostgreSQL {@code uuid} and TigerBeetle {@code UInt128} compare them. */
public final class TxnIdOrder {

  private TxnIdOrder() {
  }

  public static int compareUnsigned(UUID a, UUID b) {
    int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
    return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
  }
}
