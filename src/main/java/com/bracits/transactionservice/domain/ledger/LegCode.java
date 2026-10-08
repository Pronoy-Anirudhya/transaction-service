package com.bracits.transactionservice.domain.ledger;

/** TigerBeetle transfer {@code code} per leg type (spec 6.2). */
public enum LegCode {
  FUNDING(1),
  PRINCIPAL(10),
  FEE(11),
  VAT(12),
  COMMISSION(13);

  private final int code;

  LegCode(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
