package com.bracits.transactionservice.domain.txn.model;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * SHA-256 of the canonical Send Money request body ({@code send_money_txn.request_hash}).
 */
public record RequestHash(byte[] value) {

  public RequestHash {
    value = value.clone();
  }

  @Override
  public byte[] value() {
    return value.clone();
  }

  /**
   * Constant-time comparison.
   */
  public boolean matches(RequestHash other) {
    return MessageDigest.isEqual(value, other.value);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RequestHash other && Arrays.equals(value, other.value);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(value);
  }

  @Override
  public String toString() {
    return HexFormat.of().formatHex(value);
  }
}
