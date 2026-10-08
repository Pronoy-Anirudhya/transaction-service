package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.adapter.out.jdbc.sql.SqlParams;
import com.bracits.transactionservice.domain.wallet.NewWallet;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/** Wallet domain values → named SQL parameters of {@code WalletSql}. */
@Component
public final class WalletParamMapper {

  public Map<String, Object> byMsisdn(String msisdn) {
    return Map.of(SqlParams.MSISDN, msisdn);
  }

  public Map<String, Object> byId(long walletId) {
    return Map.of(SqlParams.WALLET_ID, walletId);
  }

  /** The wallet columns plus the business day and month of its first {@code wallet_limit_usage} row. */
  public Map<String, Object> insert(NewWallet wallet, LocalDate businessDate) {
    return Map.of(
        SqlParams.MSISDN, wallet.msisdn(),
        SqlParams.HOLDER_NAME, wallet.holderName(),
        SqlParams.WALLET_TYPE, wallet.type().name(),
        SqlParams.STATUS, wallet.status().name(),
        SqlParams.KYC_TIER, wallet.kycTier(),
        SqlParams.LEDGER_ACCOUNT_ID, wallet.ledgerAccountId(),
        SqlParams.BUSINESS_DATE, businessDate,
        SqlParams.MONTH, businessDate.withDayOfMonth(1));
  }
}
