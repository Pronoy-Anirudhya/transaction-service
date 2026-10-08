package com.bracits.transactionservice.adapter.out.ledger.model;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * A raw ledger answer: status and body bytes. Bodies are parsed only after the status is
 * classified.
 */
public record LedgerHttpResponse(HttpStatusCode status, byte[] body) {

  public boolean is(HttpStatus expected) {
    return status.isSameCodeAs(expected);
  }

  public int statusValue() {
    return status.value();
  }
}
