package com.bracits.transactionservice.domain.fee;

/** {@code fee_rule.fee_type}: FLAT = {@code fee_value} poisha; PERCENT = {@code fee_value} basis points of the amount. */
public enum FeeType {
  FLAT,
  PERCENT
}
