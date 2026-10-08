package com.bracits.transactionservice.adapter.out.jdbc.mapper.impl;

import com.bracits.transactionservice.adapter.out.jdbc.constant.SqlParams;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.LimitParamMapper;
import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link LimitParamMapper}.
 */
@Component
public final class LimitParamMapperImpl implements LimitParamMapper {

  @Override
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
