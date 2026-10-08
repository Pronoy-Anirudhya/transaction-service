package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.mapper.SendMoneyEventMapper;
import com.bracits.transactionservice.domain.ledger.PostingOutcome;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.port.out.EventPublisherPort;
import com.bracits.transactionservice.port.out.TxnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.time.Duration;
import java.util.UUID;

/**
 * Spec 5 step 8 / 8.3 outcome table, shared by the request path and the repair worker. Each finalising write is ONE
 * auto-commit compare-and-set statement; the event is handed to the publisher only after it committed (spec 9 rule 1).
 * A definitive 422 is the only way to FAILED.
 */
@Component
public final class TxnFinaliser {

  private static final Logger LOG = LoggerFactory.getLogger(TxnFinaliser.class);

  private final TxnRepository txns;
  private final EventPublisherPort events;
  private final SendMoneyEventMapper eventMapper;

  public TxnFinaliser(TxnRepository txns, EventPublisherPort events, SendMoneyEventMapper eventMapper) {
    this.txns = txns;
    this.events = events;
    this.eventMapper = eventMapper;
  }

  /**
   * @param recheckDelay when the outcome is unknown, the row is rechecked by repair after this delay
   */
  public Finalised finalise(UUID txnId, PostingOutcome outcome, Duration recheckDelay) {
    try {
      return switch (outcome) {
        case PostingOutcome.Posted posted -> txns.markCompleted(txnId, posted.ledgerTimestamp())
            .map(this::publish)
            .<Finalised>map(Finalised.Final::new)
            .orElseGet(() -> current(txnId));
        case PostingOutcome.Rejected rejected -> txns.markFailedAndReleaseLimits(txnId, rejected.code())
            .map(this::publish)
            .<Finalised>map(Finalised.Final::new)
            .orElseGet(() -> current(txnId));
        case PostingOutcome.Unknown unknown -> {
          switch (unknown.reason()) {
            case POSTING_CONFLICT -> LOG.error(ApplicationConstants.ALERT_POSTING_CONFLICT);
            case LEDGER_ERROR -> LOG.error(ApplicationConstants.ALERT_LEDGER_ERROR);
            case LEDGER_TIMEOUT, OVERLOADED -> LOG.warn(ApplicationConstants.LOG_LEDGER_UNKNOWN, unknown.reason());
          }
          txns.scheduleRecheck(txnId, recheckDelay);
          yield new Finalised.InDoubt();
        }
      };
    } catch (DataAccessException | TransactionException e) {
      // F8: money may have moved; the row stays INITIATED and repair finalises it once PostgreSQL is back.
      LOG.warn(ApplicationConstants.LOG_FINALISE_FAILED);
      return new Finalised.InDoubt();
    }
  }

  /** Hands the event of a committed final row to the asynchronous publisher. */
  public SendMoneyTxn publish(SendMoneyTxn txn) {
    events.publish(eventMapper.toEvent(txn));
    return txn;
  }

  /** The CAS found the row already final (another instance or the repair worker won the race): report its state. */
  private Finalised current(UUID txnId) {
    LOG.info(ApplicationConstants.LOG_ALREADY_FINALISED);
    return txns.findById(txnId)
        .filter(txn -> txn.status().isFinal())
        .<Finalised>map(Finalised.Final::new)
        .orElseGet(Finalised.InDoubt::new);
  }

  /** Result of finalisation. */
  public sealed interface Finalised {

    /** The row is COMPLETED or FAILED. */
    record Final(SendMoneyTxn txn) implements Finalised {
    }

    /** The row is still INITIATED; repair will resolve it. */
    record InDoubt() implements Finalised {
    }
  }
}
