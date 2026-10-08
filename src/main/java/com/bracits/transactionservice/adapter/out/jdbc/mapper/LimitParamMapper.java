package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.domain.limit.model.LimitReservation;
import java.util.Map;

/**
 * {@link LimitReservation} → named SQL parameters of {@code LimitSql.RESERVE}.
 */
public interface LimitParamMapper {

  Map<String, Object> reserve(LimitReservation reservation);
}
