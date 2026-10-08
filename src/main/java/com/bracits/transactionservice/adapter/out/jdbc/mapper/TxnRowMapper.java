package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.adapter.out.jdbc.sql.TxnColumns;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.txn.RequestHash;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

/** {@code send_money_txn} row → {@link SendMoneyTxn}; nullable columns become Optional / OptionalLong. */
@Component
public final class TxnRowMapper implements RowMapper<SendMoneyTxn> {

  @Override
  public SendMoneyTxn mapRow(ResultSet rs, int rowNum) throws SQLException {
    Pricing pricing = new Pricing(
        rs.getLong(TxnColumns.FEE),
        rs.getLong(TxnColumns.VAT),
        rs.getLong(TxnColumns.COMMISSION),
        rs.getLong(TxnColumns.FEE_INCOME));
    return new SendMoneyTxn(
        ResultSetValues.uuid(rs, TxnColumns.TXN_ID),
        rs.getString(TxnColumns.CLIENT_REF),
        new RequestHash(rs.getBytes(TxnColumns.REQUEST_HASH)),
        rs.getLong(TxnColumns.SENDER_WALLET_ID),
        rs.getLong(TxnColumns.RECEIVER_WALLET_ID),
        rs.getLong(TxnColumns.AMOUNT),
        pricing,
        rs.getString(TxnColumns.CURRENCY),
        ResultSetValues.optionalString(rs, TxnColumns.REFERENCE),
        ResultSetValues.date(rs, TxnColumns.BUSINESS_DATE),
        TxnStatus.valueOf(rs.getString(TxnColumns.STATUS)),
        ResultSetValues.optionalString(rs, TxnColumns.FAILURE_CODE).map(FailureCode::valueOf),
        rs.getInt(TxnColumns.LEDGER_ATTEMPTS),
        ResultSetValues.optionalInstant(rs, TxnColumns.NEXT_CHECK_AT),
        ResultSetValues.optionalLong(rs, TxnColumns.LEDGER_TS),
        ResultSetValues.instant(rs, TxnColumns.CREATED_AT),
        ResultSetValues.optionalInstant(rs, TxnColumns.COMPLETED_AT),
        ResultSetValues.optionalInstant(rs, TxnColumns.EVENT_PUBLISHED_AT));
  }
}
