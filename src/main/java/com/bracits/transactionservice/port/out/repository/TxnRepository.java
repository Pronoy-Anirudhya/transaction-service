package com.bracits.transactionservice.port.out.repository;

import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * {@code send_money_txn}. One SQL statement per method. Every finalising update is compare-and-set
 * on the status, so a request thread, the repair worker and reconciliation can never both finalise
 * one row.
 */
public interface TxnRepository {

  /**
   * {@code INSERT … (status 'INITIATED', next_check_at = now()) ON CONFLICT (sender_wallet_id,
   * client_ref) DO NOTHING RETURNING txn_id}. Runs inside DB transaction #1.
   *
   * @return the txnId if inserted; empty if (sender, clientRef) already exists (a duplicate
   * request)
   */
  Optional<UUID> insertIfAbsent(NewSendMoneyTxn txn);

  Optional<SendMoneyTxn> findBySenderAndClientRef(long senderWalletId, String clientRef);

  Optional<SendMoneyTxn> findById(UUID txnId);

  /**
   * {@code UPDATE … SET status='COMPLETED', ledger_ts=?, completed_at=now() WHERE txn_id=? AND
   * status='INITIATED' RETURNING *}.
   *
   * @return the updated row, or empty if the row was not INITIATED (someone else finalised it)
   */
  Optional<SendMoneyTxn> markCompleted(UUID txnId, long ledgerTimestamp);

  /**
   * The single CTE statement of spec 6.1: mark FAILED with {@code code} (CAS on INITIATED) and
   * release the limit usage only if the counters still belong to the transaction's day / month.
   *
   * @return the updated row, or empty if the row was not INITIATED
   */
  Optional<SendMoneyTxn> markFailedAndReleaseLimits(UUID txnId, FailureCode code);

  /**
   * Outcome unknown:
   * {@code SET next_check_at = now() + delay, ledger_attempts = ledger_attempts + 1 WHERE txn_id =
   * ? AND status = 'INITIATED'}.
   *
   * @return true if the row was still INITIATED
   */
  boolean scheduleRecheck(UUID txnId, Duration delay);

  /**
   * Repair claim (spec 8.4), ONE statement, auto-commit: select up to {@code limit} rows
   * {@code WHERE status='INITIATED' AND next_check_at <= now() AND created_at < now() - minAge
   * ORDER BY next_check_at FOR UPDATE SKIP LOCKED}, push their {@code next_check_at} to
   * {@code now() + lease}, and return them.
   */
  List<SendMoneyTxn> claimInDoubt(int limit, Duration minAge, Duration lease);

  /**
   * Republisher claim (spec 9 rule 6), ONE statement, auto-commit: rows
   * {@code WHERE status <> 'INITIATED' AND event_published_at IS NULL AND completed_at < now() -
   * minAge AND (next_check_at IS NULL OR next_check_at <= now())}
   * {@code FOR UPDATE SKIP LOCKED LIMIT limit}; sets {@code next_check_at = now() + lease} as the
   * lease (the column is otherwise unused on final rows); returns them.
   */
  List<SendMoneyTxn> claimUnpublished(int limit, Duration minAge, Duration lease);

  /**
   * Batched publish mark: in one short transaction, {@code SET LOCAL synchronous_commit = off} then
   * {@code UPDATE … SET event_published_at = now() WHERE txn_id = ANY(?)}.
   *
   * @return number of rows updated
   */
  int markEventsPublished(Collection<UUID> txnIds);

  /**
   * Reconciliation window scan by primary key (txnIds are time-ordered):
   * {@code txn_id >= from AND txn_id < to ORDER BY txn_id LIMIT limit}.
   */
  List<SendMoneyTxn> findByTxnIdRange(UUID fromInclusive, UUID toExclusive, int limit);

  /**
   * "Ledger wins" (spec 8.5): ONE CTE statement that flips a FAILED row to COMPLETED (CAS on
   * FAILED; sets ledger_ts, clears failure_code, completed_at = now(), event_published_at = NULL)
   * and re-counts the limit usage if the counters still belong to the transaction's day / month.
   * {@code ledger_ts} is set to NULL when {@code ledgerTimestamp} is empty (the ledger timestamp
   * may be unknown to reconciliation).
   *
   * @return the updated row, or empty if the row was not FAILED
   */
  Optional<SendMoneyTxn> markFailedAsCompleted(UUID txnId, OptionalLong ledgerTimestamp);

  /**
   * {@code count(*) WHERE status = 'INITIATED'} (gauge {@code txn_in_doubt}; uses the partial
   * index).
   */
  long countInDoubt();

  /**
   * {@code count(*)} of final rows with {@code event_published_at IS NULL} (gauge
   * {@code events_unpublished}).
   */
  long countUnpublished();
}
