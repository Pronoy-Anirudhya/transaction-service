package com.bracits.transactionservice.adapter.out.jdbc.repository;

import com.bracits.transactionservice.adapter.out.jdbc.constant.JdbcConstants;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.WalletParamMapper;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.WalletRowMapper;
import com.bracits.transactionservice.adapter.out.jdbc.sql.WalletSql;
import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import com.bracits.transactionservice.port.out.repository.WalletRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code wallet} (+ its {@code wallet_limit_usage} row on insert) over {@link JdbcClient}; one
 * statement per method. Not the primary {@link WalletRepository}: the request path goes through the
 * Caffeine decorator.
 */
@Component(JdbcConstants.JDBC_WALLET_REPOSITORY_BEAN)
public final class JdbcWalletRepository implements WalletRepository {

  private final JdbcClient jdbc;
  private final WalletRowMapper rowMapper;
  private final WalletParamMapper paramMapper;

  public JdbcWalletRepository(JdbcClient jdbc, WalletRowMapper rowMapper,
      WalletParamMapper paramMapper) {
    this.jdbc = jdbc;
    this.rowMapper = rowMapper;
    this.paramMapper = paramMapper;
  }

  @Override
  public Optional<Wallet> findByMsisdn(String msisdn) {
    return jdbc.sql(WalletSql.FIND_BY_MSISDN).params(paramMapper.byMsisdn(msisdn)).query(rowMapper)
        .optional();
  }

  @Override
  public Optional<Wallet> findById(long walletId) {
    return jdbc.sql(WalletSql.FIND_BY_ID).params(paramMapper.byId(walletId)).query(rowMapper)
        .optional();
  }

  @Override
  public Optional<Wallet> insertIfAbsent(NewWallet wallet, LocalDate businessDate) {
    return jdbc.sql(WalletSql.INSERT_IF_ABSENT)
        .params(paramMapper.insert(wallet, businessDate))
        .query(rowMapper)
        .optional();
  }
}
