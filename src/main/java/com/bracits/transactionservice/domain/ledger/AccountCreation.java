package com.bracits.transactionservice.domain.ledger;

/** Outcome of an idempotent account creation: 201 CREATED or 200 ALREADY_EXISTS (identical). */
public enum AccountCreation {
  CREATED,
  ALREADY_EXISTS
}
