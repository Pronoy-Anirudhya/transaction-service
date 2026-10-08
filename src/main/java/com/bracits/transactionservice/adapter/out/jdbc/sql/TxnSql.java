package com.bracits.transactionservice.adapter.out.jdbc.sql;

/**
 * SQL of {@code JdbcTxnRepository}. One statement per repository method. Status predicates are
 * literals (not parameters) so that the planner can always match the partial indexes
 * {@code smt_in_doubt} and {@code smt_unpublished}. Durations are bound as milliseconds
 * ({@code :xMs * interval '1 millisecond'}).
 */
public final class TxnSql {

  private static final String SELECT_COLUMNS = """
      SELECT txn_id, client_ref, request_hash, sender_wallet_id, receiver_wallet_id, amount, fee, vat, commission,
             fee_income, currency, reference, business_date, status, failure_code, ledger_attempts, next_check_at,
             ledger_ts, created_at, completed_at, event_published_at
        FROM send_money_txn
      """;

  /**
   * DB transaction #1, step 1 (spec 5). Empty result = duplicate (sender, Idempotency-Key).
   */
  public static final String INSERT_IF_ABSENT = """
      INSERT INTO send_money_txn (txn_id, client_ref, request_hash, sender_wallet_id, receiver_wallet_id, amount,
                                  fee, vat, commission, fee_income, currency, reference, business_date,
                                  status, next_check_at)
      VALUES (:txnId, :clientRef, :requestHash, :senderWalletId, :receiverWalletId, :amount,
              :fee, :vat, :commission, :feeIncome, :currency, :reference, :businessDate,
              'INITIATED', now())
      ON CONFLICT (sender_wallet_id, client_ref) DO NOTHING
      RETURNING txn_id
      """;

  public static final String FIND_BY_SENDER_AND_CLIENT_REF = SELECT_COLUMNS + """
       WHERE sender_wallet_id = :senderWalletId AND client_ref = :clientRef
      """;

  public static final String FIND_BY_ID = SELECT_COLUMNS + """
       WHERE txn_id = :txnId
      """;

  /**
   * POSTED: compare-and-set INITIATED -> COMPLETED (spec 5 step 8).
   */
  public static final String MARK_COMPLETED = """
      UPDATE send_money_txn SET status = 'COMPLETED', ledger_ts = :ledgerTs, completed_at = now()
       WHERE txn_id = :txnId AND status = 'INITIATED'
      RETURNING *
      """;

  /**
   * REJECTED: spec 6.1 "mark FAILED and release in one round trip" verbatim; {@code t} returns the
   * whole row and a final {@code SELECT} hands it back. The release only subtracts from counters
   * that still belong to the transaction's day / month.
   */
  public static final String MARK_FAILED_AND_RELEASE_LIMITS = """
      WITH t AS (
        UPDATE send_money_txn SET status='FAILED', failure_code=:code, completed_at=now()
         WHERE txn_id = :txnId AND status = 'INITIATED'
        RETURNING *
      ), released AS (
        UPDATE wallet_limit_usage u SET
          day_amount   = u.day_amount   - CASE WHEN u.day   = t.business_date THEN t.amount ELSE 0 END,
          day_count    = u.day_count    - CASE WHEN u.day   = t.business_date THEN 1 ELSE 0 END,
          month_amount = u.month_amount - CASE WHEN u.month = date_trunc('month', t.business_date)::date THEN t.amount ELSE 0 END,
          month_count  = u.month_count  - CASE WHEN u.month = date_trunc('month', t.business_date)::date THEN 1 ELSE 0 END
        FROM t WHERE u.wallet_id = t.sender_wallet_id
      )
      SELECT * FROM t
      """;

  /**
   * "Ledger wins" (spec 8.5): compare-and-set FAILED -> COMPLETED and re-count the limit usage; the
   * exact mirror of {@link #MARK_FAILED_AND_RELEASE_LIMITS} (only counters of the transaction's day
   * / month are touched).
   */
  public static final String MARK_FAILED_AS_COMPLETED = """
      WITH t AS (
        UPDATE send_money_txn SET status='COMPLETED', ledger_ts=:ledgerTs, failure_code=NULL, completed_at=now(),
                                  event_published_at=NULL
         WHERE txn_id = :txnId AND status = 'FAILED'
        RETURNING *
      ), recounted AS (
        UPDATE wallet_limit_usage u SET
          day_amount   = u.day_amount   + CASE WHEN u.day   = t.business_date THEN t.amount ELSE 0 END,
          day_count    = u.day_count    + CASE WHEN u.day   = t.business_date THEN 1 ELSE 0 END,
          month_amount = u.month_amount + CASE WHEN u.month = date_trunc('month', t.business_date)::date THEN t.amount ELSE 0 END,
          month_count  = u.month_count  + CASE WHEN u.month = date_trunc('month', t.business_date)::date THEN 1 ELSE 0 END
        FROM t WHERE u.wallet_id = t.sender_wallet_id
      )
      SELECT * FROM t
      """;

  /**
   * UNKNOWN outcome: push the recheck and count the attempt (CAS on INITIATED).
   */
  public static final String SCHEDULE_RECHECK = """
      UPDATE send_money_txn
         SET next_check_at = now() + :delayMs * interval '1 millisecond',
             ledger_attempts = ledger_attempts + 1
       WHERE txn_id = :txnId AND status = 'INITIATED'
      """;

  /**
   * Repair claim (spec 8.4): select in-doubt rows, lease them, return them; one auto-commit
   * statement.
   */
  public static final String CLAIM_IN_DOUBT = """
      WITH c AS (
        SELECT txn_id FROM send_money_txn
         WHERE status = 'INITIATED' AND next_check_at <= now()
           AND created_at < now() - :minAgeMs * interval '1 millisecond'
         ORDER BY next_check_at
         LIMIT :limit
         FOR UPDATE SKIP LOCKED
      )
      UPDATE send_money_txn t SET next_check_at = now() + :leaseMs * interval '1 millisecond'
        FROM c WHERE t.txn_id = c.txn_id
      RETURNING t.*
      """;

  /**
   * Republisher claim (spec 9 rule 6), lease pattern of 8.4 on {@code next_check_at}; one
   * auto-commit statement.
   */
  public static final String CLAIM_UNPUBLISHED = """
      WITH c AS (
        SELECT txn_id FROM send_money_txn
         WHERE status <> 'INITIATED' AND event_published_at IS NULL
           AND completed_at < now() - :minAgeMs * interval '1 millisecond'
           AND (next_check_at IS NULL OR next_check_at <= now())
         ORDER BY completed_at
         LIMIT :limit
         FOR UPDATE SKIP LOCKED
      )
      UPDATE send_money_txn t SET next_check_at = now() + :leaseMs * interval '1 millisecond'
        FROM c WHERE t.txn_id = c.txn_id
      RETURNING t.*
      """;

  /**
   * Losing the publish mark only causes a harmless republish (spec 9 rule 4).
   */
  public static final String SET_LOCAL_SYNCHRONOUS_COMMIT_OFF = """
      SET LOCAL synchronous_commit = off
      """;

  public static final String MARK_EVENTS_PUBLISHED = """
      UPDATE send_money_txn SET event_published_at = now()
       WHERE txn_id = ANY(:txnIds) AND event_published_at IS NULL
      """;

  /**
   * Reconciliation window scan by primary key; txnIds are time-ordered (spec 6.3).
   */
  public static final String FIND_BY_TXN_ID_RANGE = SELECT_COLUMNS + """
       WHERE txn_id >= :fromTxnId AND txn_id < :toTxnId
       ORDER BY txn_id
       LIMIT :limit
      """;

  /**
   * Gauge {@code txn_in_doubt}; answered from the partial index {@code smt_in_doubt}.
   */
  public static final String COUNT_IN_DOUBT = """
      SELECT count(*) FROM send_money_txn WHERE status = 'INITIATED'
      """;

  /**
   * Gauge {@code events_unpublished}; answered from the partial index {@code smt_unpublished}.
   */
  public static final String COUNT_UNPUBLISHED = """
      SELECT count(*) FROM send_money_txn WHERE status <> 'INITIATED' AND event_published_at IS NULL
      """;

  private TxnSql() {
  }
}
