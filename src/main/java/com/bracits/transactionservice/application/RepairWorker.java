package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.SendMoneyMetrics.MetricOutcome;
import com.bracits.transactionservice.application.TxnFinaliser.Finalised;
import com.bracits.transactionservice.config.LogConstants;
import com.bracits.transactionservice.config.PropertyConstants;
import com.bracits.transactionservice.config.RepairProperties;
import com.bracits.transactionservice.domain.ledger.LegPlanner;
import com.bracits.transactionservice.domain.ledger.PostingOutcome;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.port.out.TxnRepository;
import com.bracits.transactionservice.port.out.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * FR-06 / spec 8.4: resolves in-doubt transactions from the ledger's answer. Claim-then-process: one short auto-commit
 * statement claims rows with {@code FOR UPDATE SKIP LOCKED} and a lease; the identical posting is then re-sent outside
 * any DB transaction and finalised with the same compare-and-set statements as the request path. Never marks FAILED
 * without a 422 from the ledger. Runs in every instance.
 */
@Component
public class RepairWorker {

  private static final Logger LOG = LoggerFactory.getLogger(RepairWorker.class);
  private static final int MAX_DOUBLINGS = 30;

  private final TxnRepository txns;
  private final WalletRepository wallets;
  private final LegPlanner legPlanner;
  private final LedgerPoster ledger;
  private final TxnFinaliser finaliser;
  private final SendMoneyMetrics metrics;
  private final RepairProperties properties;

  public RepairWorker(
      TxnRepository txns,
      WalletRepository wallets,
      LegPlanner legPlanner,
      LedgerPoster ledger,
      TxnFinaliser finaliser,
      SendMoneyMetrics metrics,
      RepairProperties properties) {
    this.txns = txns;
    this.wallets = wallets;
    this.legPlanner = legPlanner;
    this.ledger = ledger;
    this.finaliser = finaliser;
    this.metrics = metrics;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = PropertyConstants.REPAIR_INTERVAL_PLACEHOLDER)
  public void run() {
    List<SendMoneyTxn> claimed;
    try {
      claimed = txns.claimInDoubt(properties.batchSize(), properties.minAge(), properties.lease());
    } catch (DataAccessException e) {
      LOG.warn(ApplicationConstants.LOG_REPAIR_CLAIM_FAILED);
      return;
    }
    if (claimed.isEmpty()) {
      return;
    }
    LOG.info(ApplicationConstants.LOG_REPAIR_CLAIMED, claimed.size());
    Semaphore permits = new Semaphore(properties.parallelism());
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      claimed.forEach(txn -> executor.execute(() -> repairWithPermit(txn, permits)));
    }
  }

  private void repairWithPermit(SendMoneyTxn txn, Semaphore permits) {
    try {
      permits.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    MDC.put(LogConstants.MDC_TXN_ID, txn.txnId().toString());
    try {
      repair(txn);
    } catch (RuntimeException e) {
      // The lease expires and the row is claimed again.
      LOG.error(ApplicationConstants.LOG_REPAIR_FAILED, e);
    } finally {
      MDC.remove(LogConstants.MDC_TXN_ID);
      permits.release();
    }
  }

  /** Re-sends the identical posting (the safest probe: it is idempotent) and finalises from the answer. */
  void repair(SendMoneyTxn txn) {
    Optional<Wallet> sender = wallets.findById(txn.senderWalletId());
    Optional<Wallet> receiver = wallets.findById(txn.receiverWalletId());
    if (sender.isEmpty() || receiver.isEmpty()) {
      LOG.error(ApplicationConstants.ALERT_WALLET_MISSING, sender.isEmpty() ? txn.senderWalletId() : txn.receiverWalletId());
      txns.scheduleRecheck(txn.txnId(), properties.maxBackoff());
      return;
    }
    PostingOutcome outcome = ledger.post(
        legPlanner.plan(txn.txnId(), sender.get(), receiver.get(), txn.amount(), txn.pricing()));
    Finalised finalised = finaliser.finalise(txn.txnId(), outcome, backoff(txn.ledgerAttempts()));
    switch (finalised) {
      case Finalised.Final(SendMoneyTxn row) -> {
        metrics.repairAttempt(row.status().name().toLowerCase(Locale.ROOT));
        LOG.info(ApplicationConstants.LOG_REPAIR_RESOLVED, row.status());
      }
      case Finalised.InDoubt() -> {
        metrics.repairAttempt(MetricOutcome.IN_DOUBT.tag());
        int attempts = txn.ledgerAttempts() + 1;
        if (attempts >= properties.alertAfterAttempts()) {
          LOG.error(ApplicationConstants.ALERT_REPAIR_ATTEMPTS, attempts);
        }
      }
    }
  }

  /** 1 s, 2 s, 4 s … capped at {@code maxBackoff} (60 s). */
  Duration backoff(int attemptsSoFar) {
    Duration delay = properties.initialBackoff().multipliedBy(1L << Math.min(Math.max(attemptsSoFar, 0), MAX_DOUBLINGS));
    return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
  }
}
