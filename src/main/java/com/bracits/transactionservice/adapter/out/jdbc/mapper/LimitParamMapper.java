package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.adapter.out.jdbc.sql.SqlParams;
import com.bracits.transactionservice.domain.limit.LimitReservation;
import com.bracits.transactionservice.domain.limit.LimitRule;
import org.springframework.stereotype.Component;

import java.util.Map;

/** {@link LimitReservation} → named SQL parameters of {@code LimitSql.RESERVE}. */
@Component
public final class LimitParamMapper {

  public Map<String, Object> reserve(LimitReservation reservation) {
    LimitRule rule = reservation.rule();
    return Map.of(
        SqlParams.SENDER, reservation.senderWalletId(),
        SqlParams.TODAY, reservation.businessDate(),
        SqlParams.MONTH, reservation.month(),
        SqlParams.AMT, reservation.amount(),
        SqlParams.DAILY_AMOUNT, rule.dailyAmount(),
        SqlParams.DAILY_COUNT, rule.dailyCount(),
        SqlParams.MONTHLY_AMOUNT, rule.monthlyAmount(),
        SqlParams.MONTHLY_COUNT, rule.monthlyCount());
  }
}
