package com.bracits.transactionservice.domain.limit.model;

import java.time.LocalDate;

/**
 * Input of the conditional limit update (spec 6.1): add {@code amount} and one count to the
 * sender's day and month counters, only if the result stays within {@code rule}.
 * {@code businessDate} is in Asia/Dhaka.
 */
public record LimitReservation(long senderWalletId, LocalDate businessDate, long amount,
                               LimitRule rule) {

  /**
   * First day of the business month.
   */
  public LocalDate month() {
    return businessDate.withDayOfMonth(1);
  }
}
