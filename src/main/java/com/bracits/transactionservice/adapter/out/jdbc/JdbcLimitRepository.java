package com.bracits.transactionservice.adapter.out.jdbc;

import com.bracits.transactionservice.adapter.out.jdbc.mapper.LimitParamMapper;
import com.bracits.transactionservice.adapter.out.jdbc.sql.LimitSql;
import com.bracits.transactionservice.domain.limit.LimitReservation;
import com.bracits.transactionservice.port.out.LimitRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code wallet_limit_usage}: the conditional limit update of spec 6.1. Runs in the caller's transaction. */
@Component
public final class JdbcLimitRepository implements LimitRepository {

  private final JdbcClient jdbc;
  private final LimitParamMapper paramMapper;

  public JdbcLimitRepository(JdbcClient jdbc, LimitParamMapper paramMapper) {
    this.jdbc = jdbc;
    this.paramMapper = paramMapper;
  }

  @Override
  public boolean reserve(LimitReservation reservation) {
    return jdbc.sql(LimitSql.RESERVE).params(paramMapper.reserve(reservation)).update() == 1;
  }
}
