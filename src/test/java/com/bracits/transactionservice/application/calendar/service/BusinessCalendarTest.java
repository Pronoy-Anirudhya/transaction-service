package com.bracits.transactionservice.application.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.application.calendar.service.impl.BusinessCalendarImpl;
import com.bracits.transactionservice.config.properties.BusinessProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

  @Test
  void todayIsTheDateInTheBusinessZoneNotUtc() {
    // 20:30 UTC on 7 Oct is 02:30 on 8 Oct in Asia/Dhaka (UTC+6).
    Clock clock = Clock.fixed(Instant.parse("2026-10-07T20:30:00Z"), ZoneOffset.UTC);
    BusinessCalendar calendar = new BusinessCalendarImpl(clock,
        new BusinessProperties(ZoneId.of("Asia/Dhaka")));

    assertThat(calendar.today()).isEqualTo(LocalDate.parse("2026-10-08"));
  }
}
