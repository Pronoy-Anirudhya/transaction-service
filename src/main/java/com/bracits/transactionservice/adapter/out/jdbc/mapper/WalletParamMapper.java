package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import java.time.LocalDate;
import java.util.Map;

/**
 * Wallet domain values → named SQL parameters of {@code WalletSql}.
 */
public interface WalletParamMapper {

  Map<String, Object> byMsisdn(String msisdn);

  Map<String, Object> byId(long walletId);

  /**
   * The wallet columns plus the business day and month of its first {@code wallet_limit_usage}
   * row.
   */
  Map<String, Object> insert(NewWallet wallet, LocalDate businessDate);
}
