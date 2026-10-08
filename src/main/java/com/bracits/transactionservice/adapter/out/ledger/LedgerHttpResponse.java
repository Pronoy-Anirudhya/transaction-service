package com.bracits.transactionservice.adapter.out.ledger;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** A raw ledger answer: status and body bytes. Bodies are parsed only after the status is classified. */
record LedgerHttpResponse(HttpStatusCode status, byte[] body) {

  boolean is(HttpStatus expected) {
    return status.isSameCodeAs(expected);
  }

  int statusValue() {
    return status.value();
  }
}
