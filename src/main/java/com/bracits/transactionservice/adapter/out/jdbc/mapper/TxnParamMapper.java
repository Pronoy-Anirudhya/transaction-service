package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import static java.util.Map.entry;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Transaction domain values → named SQL parameters of {@code TxnSql}. Durations are bound as
 * milliseconds.
 */
public interface TxnParamMapper {

  Map<String, Object> insert(NewSendMoneyTxn txn);

  Map<String, Object> bySenderAndClientRef(long senderWalletId, String clientRef);

  Map<String, Object> byId(UUID txnId);

  Map<String, Object> completed(UUID txnId, long ledgerTimestamp);

  /**
   * "Ledger wins": an unknown ledger timestamp is bound as a bigint NULL.
   */
  Map<String, Object> failedAsCompleted(UUID txnId, OptionalLong ledgerTimestamp);

  Map<String, Object> failed(UUID txnId, FailureCode code);

  Map<String, Object> recheck(UUID txnId, Duration delay);

  Map<String, Object> claim(int limit, Duration minAge, Duration lease);

  /**
   * Binds the ids as one {@code uuid[]} parameter (for {@code = ANY(:txnIds)}), not as an expanded
   * IN list.
   */
  Map<String, Object> txnIdArray(Collection<UUID> txnIds);

  Map<String, Object> txnIdRange(UUID fromInclusive, UUID toExclusive, int limit);
}
