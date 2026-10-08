package com.bracits.transactionservice.adapter.out.jdbc.mapper.impl;

import com.bracits.transactionservice.adapter.out.jdbc.constant.WalletColumns;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.WalletRowMapper;
import com.bracits.transactionservice.adapter.out.jdbc.util.ResultSetValues;
import com.bracits.transactionservice.domain.wallet.enums.WalletStatus;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link WalletRowMapper}.
 */
@Component
public final class WalletRowMapperImpl implements WalletRowMapper {

  @Override
  public Wallet mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new Wallet(
        rs.getLong(WalletColumns.WALLET_ID),
        rs.getString(WalletColumns.MSISDN),
        rs.getString(WalletColumns.HOLDER_NAME),
        WalletType.valueOf(rs.getString(WalletColumns.WALLET_TYPE)),
        WalletStatus.valueOf(rs.getString(WalletColumns.STATUS)),
        rs.getInt(WalletColumns.KYC_TIER),
        ResultSetValues.uuid(rs, WalletColumns.LEDGER_ACCOUNT_ID));
  }
}
