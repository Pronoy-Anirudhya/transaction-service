package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.adapter.out.jdbc.sql.WalletColumns;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import com.bracits.transactionservice.domain.wallet.WalletType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

/** {@code wallet} row → {@link Wallet}. */
@Component
public final class WalletRowMapper implements RowMapper<Wallet> {

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
