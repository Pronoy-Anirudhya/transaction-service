package com.bracits.transactionservice.adapter.out.jdbc.mapper.impl;

import com.bracits.transactionservice.adapter.out.jdbc.constant.SqlParams;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.WalletParamMapper;
import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link WalletParamMapper}.
 */
@Component
public final class WalletParamMapperImpl implements WalletParamMapper {

  @Override
  public Map<String, Object> byMsisdn(String msisdn) {
    return Map.of(SqlParams.MSISDN, msisdn);
  }

  @Override
  public Map<String, Object> byId(long walletId) {
    return Map.of(SqlParams.WALLET_ID, walletId);
  }

  /**
   * The wallet columns plus the business day and month of its first {@code wallet_limit_usage}
   * row.
   */
  @Override
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
