package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.result.ReconciliationResult;
import com.bracits.transactionservice.application.result.ReconciliationResult.Action;
import com.bracits.transactionservice.application.result.ReconciliationResult.LedgerView;
import com.bracits.transactionservice.application.result.ReconciliationResult.Mismatch;
import com.bracits.transactionservice.config.LogConstants;
import com.bracits.transactionservice.config.ReconciliationProperties;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.ledger.LegPlanner;
import com.bracits.transactionservice.domain.ledger.PostingLookup;
import com.bracits.transactionservice.domain.ledger.PostingLookupStatus;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.domain.txn.TxnIds;
import com.bracits.transactionservice.port.out.LedgerQueryPort;
import com.bracits.transactionservice.port.out.LedgerUnavailableException;
import com.bracits.transactionservice.port.out.TxnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * FR-08 / spec 8.5: compares final transaction records with ledger postings for a window. "The ledger wins": a FAILED
 * row whose legs are posted is flipped to COMPLETED (limits re-counted, corrective {@code SendMoneyCompleted}, alert).
 * Money is never adjusted to match the record. INITIATED rows are left to the repair worker.
 */
@Service
public class ReconciliationService {

  private static final Logger LOG = LoggerFactory.getLogger(ReconciliationService.class);

  private final TxnRepository txns;
  private final LedgerQueryPort ledger;
  private final TxnFinaliser finaliser;
  private final SendMoneyMetrics metrics;
  private final ReconciliationProperties properties;

  public ReconciliationService(
      TxnRepository txns,
      LedgerQueryPort ledger,
      TxnFinaliser finaliser,
      SendMoneyMetrics metrics,
      ReconciliationProperties properties) {
    this.txns = txns;
    this.ledger = ledger;
    this.finaliser = finaliser;
    this.metrics = metrics;
    this.properties = properties;
  }

  /** Window {@code [from, to)} by txnId (time-ordered primary key; no extra index, decision B10). */
  public ReconciliationResult reconcile(Instant from, Instant to) {
    List<SendMoneyTxn> rows = txns.findByTxnIdRange(
        TxnIds.lowerBound(from), TxnIds.lowerBound(to), properties.maxRows() + 1);
    boolean truncated = rows.size() > properties.maxRows();
    List<SendMoneyTxn> window = truncated ? rows.subList(0, properties.maxRows()) : rows;
    List<Mismatch> mismatches = checkAll(window);
    // NOT_CHECKED is a ledger outage, not a disagreement: it must not trip the mismatch alert.
    mismatches.stream().filter(mismatch -> mismatch.action() != Action.NOT_CHECKED)
        .forEach(mismatch -> metrics.reconciliationMismatch());
    return new ReconciliationResult(from, to, window.size(), truncated, mismatches);
  }

  private List<Mismatch> checkAll(List<SendMoneyTxn> rows) {
    Semaphore permits = new Semaphore(properties.parallelism());
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Optional<Mismatch>>> checks = rows.stream()
          .map(row -> executor.submit(() -> checkWithPermit(row, permits)))
          .toList();
      return checks.stream().map(ReconciliationService::await).flatMap(Optional::stream).toList();
    }
  }

  private Optional<Mismatch> checkWithPermit(SendMoneyTxn row, Semaphore permits) throws InterruptedException {
    permits.acquire();
    MDC.put(LogConstants.MDC_TXN_ID, row.txnId().toString());
    try {
      return check(row);
    } finally {
      MDC.remove(LogConstants.MDC_TXN_ID);
      permits.release();
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
      return Optional.of(new Mismatch(row.txnId(), row.status(), LedgerView.UNKNOWN, Action.NOT_CHECKED));
    } catch (RuntimeException e) {
      // One bad answer must not abort the whole report.
      LOG.error(ApplicationConstants.LOG_RECONCILIATION_LOOKUP_FAILED, e);
      return Optional.of(new Mismatch(row.txnId(), row.status(), LedgerView.UNKNOWN, Action.NOT_CHECKED));
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
    return new Mismatch(row.txnId(), row.status(), LedgerView.NOT_FOUND, Action.ALERT_RAISED);
  }

  /** Empty when the compare-and-set lost (another run already flipped the row): nothing to report. */
  private Optional<Mismatch> ledgerWins(SendMoneyTxn row, PostingLookup lookup) {
    return txns.markFailedAsCompleted(row.txnId(), lookup.ledgerTimestamp())
        .map(flipped -> {
          LOG.error(ApplicationConstants.ALERT_LEDGER_WINS);
          finaliser.publish(flipped);
          return new Mismatch(row.txnId(), row.status(), LedgerView.POSTED, Action.FLIPPED_TO_COMPLETED);
        });
  }

  private static Optional<Mismatch> await(Future<Optional<Mismatch>> check) {
    try {
      return check.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e.getCause());
    }
  }
}
