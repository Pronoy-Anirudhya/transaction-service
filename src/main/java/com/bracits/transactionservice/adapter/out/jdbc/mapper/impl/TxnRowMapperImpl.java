package com.bracits.transactionservice.adapter.out.jdbc.mapper.impl;

import com.bracits.transactionservice.adapter.out.jdbc.constant.TxnColumns;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.TxnRowMapper;
import com.bracits.transactionservice.adapter.out.jdbc.util.ResultSetValues;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link TxnRowMapper}.
 */
@Component
public final class TxnRowMapperImpl implements TxnRowMapper {

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
