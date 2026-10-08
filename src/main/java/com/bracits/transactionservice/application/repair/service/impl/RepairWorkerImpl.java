package com.bracits.transactionservice.application.repair.service.impl;

import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.enums.MetricOutcome;
import com.bracits.transactionservice.application.metrics.service.SendMoneyMetrics;
import com.bracits.transactionservice.application.posting.service.LedgerPoster;
import com.bracits.transactionservice.application.posting.service.TxnFinaliser;
import com.bracits.transactionservice.application.repair.service.RepairWorker;
import com.bracits.transactionservice.application.result.FinaliseResult;
import com.bracits.transactionservice.application.util.BoundedParallel;
import com.bracits.transactionservice.config.constant.LogConstants;
import com.bracits.transactionservice.config.constant.PropertyConstants;
import com.bracits.transactionservice.config.properties.RepairProperties;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.planner.LegPlanner;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import com.bracits.transactionservice.port.out.client.LedgerHealthPort;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import com.bracits.transactionservice.port.out.repository.WalletRepository;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link RepairWorker}.
 */
@Component
public class RepairWorkerImpl implements RepairWorker {

  private static final Logger LOG = LoggerFactory.getLogger(RepairWorkerImpl.class);
  private static final int MAX_DOUBLINGS = 30;

  private final TxnRepository txns;
  private final WalletRepository wallets;
  private final LegPlanner legPlanner;
  private final LedgerPoster ledger;
  private final TxnFinaliser finaliser;
  private final SendMoneyMetrics metrics;
  private final RepairProperties properties;
  private final LedgerHealthPort ledgerHealth;

  public RepairWorkerImpl(
      TxnRepository txns,
      WalletRepository wallets,
      LegPlanner legPlanner,
      LedgerPoster ledger,
      TxnFinaliser finaliser,
      SendMoneyMetrics metrics,
      RepairProperties properties,
      LedgerHealthPort ledgerHealth) {
    this.txns = txns;
    this.wallets = wallets;
    this.legPlanner = legPlanner;
    this.ledger = ledger;
    this.finaliser = finaliser;
    this.metrics = metrics;
    this.properties = properties;
    this.ledgerHealth = ledgerHealth;
  }

  @Scheduled(fixedDelayString = PropertyConstants.REPAIR_INTERVAL_PLACEHOLDER)
  @Override
  public void run() {
    if (!ledgerHealth.isAvailable()) {
      // Nothing can be resolved without the ledger; keep the rows for when it is back.
      LOG.debug(ApplicationConstants.LOG_REPAIR_PAUSED);
      return;
    }

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
    BoundedParallel.forEach(claimed, properties.parallelism(), this::repairSafely);
  }

  private void repairSafely(SendMoneyTxn txn) {
    MDC.put(LogConstants.MDC_TXN_ID, txn.txnId().toString());
    try {
      repair(txn);
    } catch (RuntimeException e) {
      // The lease expires and the row is claimed again.
      LOG.error(ApplicationConstants.LOG_REPAIR_FAILED, e);
    } finally {
      MDC.remove(LogConstants.MDC_TXN_ID);
    }
  }

  /**
   * Re-sends the identical posting (the safest probe: it is idempotent) and finalises from the
   * answer.
   */
  void repair(SendMoneyTxn txn) {
    Optional<Wallet> sender = wallets.findById(txn.senderWalletId());
    Optional<Wallet> receiver = wallets.findById(txn.receiverWalletId());
    if (sender.isEmpty() || receiver.isEmpty()) {
      LOG.error(ApplicationConstants.ALERT_WALLET_MISSING,
          sender.isEmpty() ? txn.senderWalletId() : txn.receiverWalletId());
      txns.scheduleRecheck(txn.txnId(), properties.maxBackoff());
      return;
    }

    PostingOutcome outcome = ledger.post(
        legPlanner.plan(txn.txnId(), sender.get(), receiver.get(), txn.amount(), txn.pricing()));

    FinaliseResult finalised = finaliser.finalise(txn.txnId(), outcome,
        backoff(txn.ledgerAttempts()));
    switch (finalised) {
      case FinaliseResult.Final(SendMoneyTxn row) -> {
        metrics.repairAttempt(row.status().name().toLowerCase(Locale.ROOT));
        LOG.info(ApplicationConstants.LOG_REPAIR_RESOLVED, row.status());
      }
      case FinaliseResult.InDoubt() -> {
        metrics.repairAttempt(MetricOutcome.IN_DOUBT.tag());
        int attempts = txn.ledgerAttempts() + 1;
        if (attempts >= properties.alertAfterAttempts()) {
          LOG.error(ApplicationConstants.ALERT_REPAIR_ATTEMPTS, attempts);
        }
      }
    }
  }

  /**
   * 1 s, 2 s, 4 s … capped at {@code maxBackoff} (60 s).
   */
  Duration backoff(int attemptsSoFar) {
    Duration delay = properties.initialBackoff()
        .multipliedBy(1L << Math.min(Math.max(attemptsSoFar, 0), MAX_DOUBLINGS));
    return delay.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : delay;
  }
}
