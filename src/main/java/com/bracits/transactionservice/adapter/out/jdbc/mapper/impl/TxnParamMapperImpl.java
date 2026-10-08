package com.bracits.transactionservice.adapter.out.jdbc.mapper.impl;

import static java.util.Map.entry;

import com.bracits.transactionservice.adapter.out.jdbc.constant.SqlParams;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.TxnParamMapper;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import java.sql.Types;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link TxnParamMapper}.
 */
@Component
public final class TxnParamMapperImpl implements TxnParamMapper {

  @Override
  public Map<String, Object> insert(NewSendMoneyTxn txn) {
    Pricing pricing = txn.pricing();
    return Map.ofEntries(
        entry(SqlParams.TXN_ID, txn.txnId()),
        entry(SqlParams.CLIENT_REF, txn.clientRef()),
        entry(SqlParams.REQUEST_HASH, txn.requestHash().value()),
        entry(SqlParams.SENDER_WALLET_ID, txn.senderWalletId()),
        entry(SqlParams.RECEIVER_WALLET_ID, txn.receiverWalletId()),
        entry(SqlParams.AMOUNT, txn.amount()),
        entry(SqlParams.FEE, pricing.fee()),
        entry(SqlParams.VAT, pricing.vat()),
        entry(SqlParams.COMMISSION, pricing.commission()),
        entry(SqlParams.FEE_INCOME, pricing.feeIncome()),
        entry(SqlParams.CURRENCY, txn.currency()),
        // nullable: typed explicitly so the driver binds a varchar NULL
        entry(SqlParams.REFERENCE, new SqlParameterValue(Types.VARCHAR, txn.reference())),
        entry(SqlParams.BUSINESS_DATE, txn.businessDate()));
  }

  @Override
  public Map<String, Object> bySenderAndClientRef(long senderWalletId, String clientRef) {
    return Map.of(SqlParams.SENDER_WALLET_ID, senderWalletId, SqlParams.CLIENT_REF, clientRef);
  }

  @Override
  public Map<String, Object> byId(UUID txnId) {
    return Map.of(SqlParams.TXN_ID, txnId);
  }

  @Override
  public Map<String, Object> completed(UUID txnId, long ledgerTimestamp) {
    return Map.of(SqlParams.TXN_ID, txnId, SqlParams.LEDGER_TS, ledgerTimestamp);
  }

  /**
   * "Ledger wins": an unknown ledger timestamp is bound as a bigint NULL.
   */
  @Override
  public Map<String, Object> failedAsCompleted(UUID txnId, OptionalLong ledgerTimestamp) {
    Long ledgerTs = ledgerTimestamp.isPresent() ? ledgerTimestamp.getAsLong() : null;
    return Map.of(SqlParams.TXN_ID, txnId, SqlParams.LEDGER_TS,
        new SqlParameterValue(Types.BIGINT, ledgerTs));
  }

  @Override
  public Map<String, Object> failed(UUID txnId, FailureCode code) {
    return Map.of(SqlParams.TXN_ID, txnId, SqlParams.CODE, code.name());
  }

  @Override
  public Map<String, Object> recheck(UUID txnId, Duration delay) {
    return Map.of(SqlParams.TXN_ID, txnId, SqlParams.DELAY_MS, delay.toMillis());
  }

  @Override
  public Map<String, Object> claim(int limit, Duration minAge, Duration lease) {
    return Map.of(
        SqlParams.LIMIT, limit,
        SqlParams.MIN_AGE_MS, minAge.toMillis(),
        SqlParams.LEASE_MS, lease.toMillis());
  }

  /**
   * Binds the ids as one {@code uuid[]} parameter (for {@code = ANY(:txnIds)}), not as an expanded
   * IN list.
   */
  @Override
  public Map<String, Object> txnIdArray(Collection<UUID> txnIds) {
    return Map.of(SqlParams.TXN_IDS, new SqlArrayValue(SqlParams.UUID_TYPE_NAME, txnIds.toArray()));
  }

  @Override
  public Map<String, Object> txnIdRange(UUID fromInclusive, UUID toExclusive, int limit) {
    return Map.of(
        SqlParams.FROM_TXN_ID, fromInclusive,
        SqlParams.TO_TXN_ID, toExclusive,
        SqlParams.LIMIT, limit);
  }
}
