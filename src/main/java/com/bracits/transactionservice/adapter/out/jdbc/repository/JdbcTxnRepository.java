package com.bracits.transactionservice.adapter.out.jdbc.repository;

import com.bracits.transactionservice.adapter.out.jdbc.mapper.TxnParamMapper;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.TxnRowMapper;
import com.bracits.transactionservice.adapter.out.jdbc.sql.TxnSql;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * {@code send_money_txn} over {@link JdbcClient}; one SQL statement per method (the publish mark
 * adds a {@code SET LOCAL} in its own short transaction). Finalising updates are compare-and-set on
 * {@code status}.
 */
@Component
public final class JdbcTxnRepository implements TxnRepository {

  private final JdbcClient jdbc;
  private final TransactionOperations transactions;
  private final TxnRowMapper rowMapper;
  private final TxnParamMapper paramMapper;

  public JdbcTxnRepository(
      JdbcClient jdbc, TransactionOperations transactions, TxnRowMapper rowMapper,
      TxnParamMapper paramMapper) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.rowMapper = rowMapper;
    this.paramMapper = paramMapper;
  }

  @Override
  public Optional<UUID> insertIfAbsent(NewSendMoneyTxn txn) {
    return jdbc.sql(TxnSql.INSERT_IF_ABSENT).params(paramMapper.insert(txn)).query(UUID.class)
        .optional();
  }

  @Override
  public Optional<SendMoneyTxn> findBySenderAndClientRef(long senderWalletId, String clientRef) {
    return queryOne(TxnSql.FIND_BY_SENDER_AND_CLIENT_REF,
        paramMapper.bySenderAndClientRef(senderWalletId, clientRef));
  }

  @Override
  public Optional<SendMoneyTxn> findById(UUID txnId) {
    return queryOne(TxnSql.FIND_BY_ID, paramMapper.byId(txnId));
  }

  @Override
  public Optional<SendMoneyTxn> markCompleted(UUID txnId, long ledgerTimestamp) {
    return queryOne(TxnSql.MARK_COMPLETED, paramMapper.completed(txnId, ledgerTimestamp));
  }

  @Override
  public Optional<SendMoneyTxn> markFailedAndReleaseLimits(UUID txnId, FailureCode code) {
    return queryOne(TxnSql.MARK_FAILED_AND_RELEASE_LIMITS, paramMapper.failed(txnId, code));
  }

  @Override
  public boolean scheduleRecheck(UUID txnId, Duration delay) {
    return jdbc.sql(TxnSql.SCHEDULE_RECHECK).params(paramMapper.recheck(txnId, delay)).update()
        == 1;
  }

  @Override
  public List<SendMoneyTxn> claimInDoubt(int limit, Duration minAge, Duration lease) {
    return queryList(TxnSql.CLAIM_IN_DOUBT, paramMapper.claim(limit, minAge, lease));
  }

  @Override
  public List<SendMoneyTxn> claimUnpublished(int limit, Duration minAge, Duration lease) {
    return queryList(TxnSql.CLAIM_UNPUBLISHED, paramMapper.claim(limit, minAge, lease));
  }

  @Override
  public int markEventsPublished(Collection<UUID> txnIds) {
    if (txnIds.isEmpty()) {
      return 0;
    }

    Map<String, Object> params = paramMapper.txnIdArray(txnIds);
    Integer updated = transactions.execute(status -> {
      jdbc.sql(TxnSql.SET_LOCAL_SYNCHRONOUS_COMMIT_OFF).update();
      return jdbc.sql(TxnSql.MARK_EVENTS_PUBLISHED).params(params).update();
    });

    return updated == null ? 0 : updated;
  }

  @Override
  public List<SendMoneyTxn> findByTxnIdRange(UUID fromInclusive, UUID toExclusive, int limit) {
    return queryList(TxnSql.FIND_BY_TXN_ID_RANGE,
        paramMapper.txnIdRange(fromInclusive, toExclusive, limit));
  }

  @Override
  public Optional<SendMoneyTxn> markFailedAsCompleted(UUID txnId, OptionalLong ledgerTimestamp) {
    return queryOne(TxnSql.MARK_FAILED_AS_COMPLETED,
        paramMapper.failedAsCompleted(txnId, ledgerTimestamp));
  }

  @Override
  public long countInDoubt() {
    return jdbc.sql(TxnSql.COUNT_IN_DOUBT).query(Long.class).single();
  }

  @Override
  public long countUnpublished() {
    return jdbc.sql(TxnSql.COUNT_UNPUBLISHED).query(Long.class).single();
  }

  private Optional<SendMoneyTxn> queryOne(String sql, Map<String, Object> params) {
    return jdbc.sql(sql).params(params).query(rowMapper).optional();
  }

  private List<SendMoneyTxn> queryList(String sql, Map<String, Object> params) {
    return jdbc.sql(sql).params(params).query(rowMapper).list();
  }
}
