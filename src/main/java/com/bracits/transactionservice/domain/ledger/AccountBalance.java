package com.bracits.transactionservice.domain.ledger;

/** Balance of one ledger account, as returned by the ledger (poisha). */
public record AccountBalance(
    long debitsPosted,
    long creditsPosted,
    long debitsPending,
    long creditsPending,
    long available) {

  /** Net posted balance of a credit-normal account (a customer wallet). */
  public long netPosted() {
    return Math.subtractExact(creditsPosted, debitsPosted);
  }

  /** Net pending balance of a credit-normal account. */
  public long netPending() {
    return Math.subtractExact(creditsPending, debitsPending);
  }
}
