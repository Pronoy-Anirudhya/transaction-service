package com.bracits.transactionservice.domain;

/** Products priced and limited by rules. {@code code} is TigerBeetle {@code user_data_32} (spec 6.2). */
public enum Product {
  SEND_MONEY(1);

  private final int code;

  Product(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
