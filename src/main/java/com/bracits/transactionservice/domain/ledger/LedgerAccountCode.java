package com.bracits.transactionservice.domain.ledger;

/** TigerBeetle account {@code code} (spec 6.2). */
public enum LedgerAccountCode {
  CUSTOMER_WALLET(100),
  FEE_INCOME(200),
  VAT_PAYABLE(210),
  COMMISSION_PAYABLE(220),
  EMONEY_ISSUANCE(900);

  private final int code;

  LedgerAccountCode(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
