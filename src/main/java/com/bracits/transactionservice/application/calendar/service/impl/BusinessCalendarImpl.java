package com.bracits.transactionservice.application.calendar.service.impl;

import com.bracits.transactionservice.application.calendar.service.BusinessCalendar;
import com.bracits.transactionservice.config.properties.BusinessProperties;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link BusinessCalendar}.
 */
@Component
public final class BusinessCalendarImpl implements BusinessCalendar {

  private final Clock clock;
  private final BusinessProperties business;

  public BusinessCalendarImpl(Clock clock, BusinessProperties business) {
    this.clock = clock;
    this.business = business;
  }

  @Override
  public LocalDate today() {
    return LocalDate.now(clock.withZone(business.zone()));
  }
}
