package com.bracits.transactionservice.application.reconciliation.service.impl;

import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.enums.ReconciliationAction;
import com.bracits.transactionservice.application.enums.ReconciliationLedgerView;
import com.bracits.transactionservice.application.metrics.service.SendMoneyMetrics;
import com.bracits.transactionservice.application.posting.service.TxnFinaliser;
import com.bracits.transactionservice.application.reconciliation.service.ReconciliationService;
import com.bracits.transactionservice.application.result.ReconciliationResult.Mismatch;
import com.bracits.transactionservice.application.result.ReconciliationResult;
import com.bracits.transactionservice.application.util.BoundedParallel;
import com.bracits.transactionservice.config.constant.LogConstants;
import com.bracits.transactionservice.config.properties.ReconciliationProperties;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import com.bracits.transactionservice.domain.ledger.enums.PostingLookupStatus;
import com.bracits.transactionservice.domain.ledger.model.PostingLookup;
import com.bracits.transactionservice.domain.ledger.planner.LegPlanner;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.domain.txn.util.TxnIds;
import com.bracits.transactionservice.port.out.client.LedgerHealthPort;
import com.bracits.transactionservice.port.out.client.LedgerQueryPort;
import com.bracits.transactionservice.port.out.exception.LedgerUnavailableException;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link ReconciliationService}.
 */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

  private static final Logger LOG = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

  private final TxnRepository txns;
  private final LedgerQueryPort ledger;
  private final TxnFinaliser finaliser;
  private final SendMoneyMetrics metrics;
  private final ReconciliationProperties properties;
  private final LedgerHealthPort ledgerHealth;

  public ReconciliationServiceImpl(
      TxnRepository txns,
      LedgerQueryPort ledger,
      TxnFinaliser finaliser,
      SendMoneyMetrics metrics,
      ReconciliationProperties properties,
      LedgerHealthPort ledgerHealth) {
    this.txns = txns;
    this.ledger = ledger;
    this.finaliser = finaliser;
    this.metrics = metrics;
    this.properties = properties;
    this.ledgerHealth = ledgerHealth;
  }

  /**
   * Window {@code [from, to)} by txnId (time-ordered primary key; no extra index, decision B10).
   */
  @Override
  public ReconciliationResult reconcile(Instant from, Instant to) {
    if (!ledgerHealth.isAvailable()) {
      throw new LedgerUnavailableException(ApplicationConstants.MSG_LEDGER_UNAVAILABLE);
    }

    List<SendMoneyTxn> rows = txns.findByTxnIdRange(
        TxnIds.lowerBound(from), TxnIds.lowerBound(to), properties.maxRows() + 1);

    boolean truncated = rows.size() > properties.maxRows();
    List<SendMoneyTxn> window = truncated ? rows.subList(0, properties.maxRows()) : rows;

    List<Mismatch> mismatches = checkAll(window);
    // NOT_CHECKED is a ledger outage, not a disagreement: it must not trip the mismatch alert.
    mismatches.stream().filter(mismatch -> mismatch.action() != ReconciliationAction.NOT_CHECKED)
        .forEach(mismatch -> metrics.reconciliationMismatch());

    return new ReconciliationResult(from, to, window.size(), truncated, mismatches);
  }

  private List<Mismatch> checkAll(List<SendMoneyTxn> rows) {
    return BoundedParallel.map(rows, properties.parallelism(), this::checkWithMdc).stream()
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<Mismatch> checkWithMdc(SendMoneyTxn row) {
    MDC.put(LogConstants.MDC_TXN_ID, row.txnId().toString());
    try {
      return check(row);
    } finally {
      MDC.remove(LogConstants.MDC_TXN_ID);
    }
  }

  Optional<Mismatch> check(SendMoneyTxn row) {
    if (row.status() == TxnStatus.INITIATED) {
      return Optional.empty();
    }

    PostingLookup lookup;
    try {
      lookup = ledger.lookupPosting(row.txnId(), LegPlanner.legCount(row.pricing()));
    } catch (LedgerUnavailableException e) {
      LOG.warn(ApplicationConstants.LOG_RECONCILIATION_LOOKUP_FAILED);
      return Optional.of(new Mismatch(row.txnId(), row.status(), ReconciliationLedgerView.UNKNOWN,
          ReconciliationAction.NOT_CHECKED));
    } catch (RuntimeException e) {
      // One bad answer must not abort the whole report.
      LOG.error(ApplicationConstants.LOG_RECONCILIATION_LOOKUP_FAILED, e);
      return Optional.of(new Mismatch(row.txnId(), row.status(), ReconciliationLedgerView.UNKNOWN,
          ReconciliationAction.NOT_CHECKED));
    }

    boolean posted = lookup.status() == PostingLookupStatus.POSTED;
    return switch (row.status()) {
      case COMPLETED -> posted
          ? Optional.empty()
          : Optional.of(alertCompletedNotPosted(row));
      case FAILED -> posted
          ? ledgerWins(row, lookup)
          : Optional.empty();
      case INITIATED -> Optional.empty();
    };
  }

  private Mismatch alertCompletedNotPosted(SendMoneyTxn row) {
    LOG.error(ApplicationConstants.ALERT_COMPLETED_NOT_POSTED);
    return new Mismatch(row.txnId(), row.status(), ReconciliationLedgerView.NOT_FOUND,
        ReconciliationAction.ALERT_RAISED);
  }

  /**
   * Empty when the compare-and-set lost (another run already flipped the row): nothing to report.
   */
  private Optional<Mismatch> ledgerWins(SendMoneyTxn row, PostingLookup lookup) {
    return txns.markFailedAsCompleted(row.txnId(), lookup.ledgerTimestamp())
        .map(flipped -> {
          LOG.error(ApplicationConstants.ALERT_LEDGER_WINS);
          finaliser.publish(flipped);
          return new Mismatch(row.txnId(), row.status(), ReconciliationLedgerView.POSTED,
              ReconciliationAction.FLIPPED_TO_COMPLETED);
        });
  }

}
