package com.bracits.transactionservice.domain;

import java.util.UUID;

/** Domain-wide constants. Pure Java: no framework types. */
public final class DomainConstants {

  /** Basis-point denominator: 10,000 bps = 100%. */
  public static final long BPS_DENOMINATOR = 10_000L;

  /** TigerBeetle ledger for BDT e-money (spec 6.2). */
  public static final int LEDGER_ID = 1;

  /** ISO 4217 code of the only supported currency (A4). */
  public static final String CURRENCY_BDT = "BDT";

  /** Maximum number of legs in one Send Money posting (spec 6.2). */
  public static final int MAX_SEND_MONEY_LEGS = 4;

  /** Number of low zero bits in a txnId, reserved for the leg index (spec 6.3). */
  public static final int LEG_INDEX_BITS = 8;

  /** Number of trailing MSISDN digits left visible when masking (spec 12). */
  public static final int MSISDN_VISIBLE_DIGITS = 3;

  /** Character used to mask hidden MSISDN digits. */
  public static final char MASK_CHAR = '*';

  /** Event payload schema version (spec 9). */
  public static final int EVENT_SCHEMA_VERSION = 1;

  /**
   * Namespace for deterministic UUIDv5 event IDs ({@code message_id} = UUIDv5(namespace, txnId + eventType)).
   * Fixed forever: changing it would break consumer de-duplication.
   */
  public static final UUID EVENT_ID_NAMESPACE = UUID.fromString("6f1d3c8e-2b7a-5c1e-9f4d-0a6b8e2c7d31");

  /** Namespace for deterministic UUIDv5 funding IDs (test-profile funding). */
  public static final UUID FUNDING_ID_NAMESPACE = UUID.fromString("0c9a7e54-3f21-5b8d-a6e4-7d2f19b3c850");

  private DomainConstants() {
  }
}
