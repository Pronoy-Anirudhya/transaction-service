package com.bracits.transactionservice.port.out.repository;

import com.bracits.transactionservice.domain.limit.model.LimitReservation;

/**
 * Sender limit usage ({@code wallet_limit_usage}).
 */
public interface LimitRepository {

  /**
   * The conditional limit update of spec 6.1, verbatim: one statement, one row lock per sender.
   * Counters of a past day/month are reset in place. Must run inside DB transaction #1 (the
   * caller's transaction).
   *
   * @return true if one row was updated (reserved); false if any daily/monthly amount/count limit
   * would be exceeded
   */
  boolean reserve(LimitReservation reservation);
}
