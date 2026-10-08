package com.bracits.transactionservice.application.calendar.service;

import java.time.LocalDate;

/**
 * The business date in {@code poc.business.zone} (Asia/Dhaka), which limits are counted against
 * (A3, NFR-10).
 */
public interface BusinessCalendar {

  LocalDate today();
}
