package com.bracits.transactionservice.application.posting.service.impl;

import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.mapper.SendMoneyEventMapper;
import com.bracits.transactionservice.application.posting.service.TxnFinaliser;
import com.bracits.transactionservice.application.result.FinaliseResult;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.port.out.publisher.EventPublisherPort;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/**
 * Default implementation of {@link TxnFinaliser}.
 */
@Component
public final class TxnFinaliserImpl implements TxnFinaliser {

  private static final Logger LOG = LoggerFactory.getLogger(TxnFinaliserImpl.class);

  private final TxnRepository txns;
  private final EventPublisherPort events;
  private final SendMoneyEventMapper eventMapper;

  public TxnFinaliserImpl(TxnRepository txns, EventPublisherPort events,
      SendMoneyEventMapper eventMapper) {
    this.txns = txns;
    this.events = events;
    this.eventMapper = eventMapper;
  }

  /**
   * @param recheckDelay when the outcome is unknown, the row is rechecked by repair after this
   *                     delay
   */
  @Override
  public FinaliseResult finalise(UUID txnId, PostingOutcome outcome, Duration recheckDelay) {
    try {
      return switch (outcome) {
        case PostingOutcome.Posted posted -> txns.markCompleted(txnId, posted.ledgerTimestamp())
            .map(this::publish)
            .<FinaliseResult>map(FinaliseResult.Final::new)
            .orElseGet(() -> current(txnId));
        case PostingOutcome.Rejected rejected ->
            txns.markFailedAndReleaseLimits(txnId, rejected.code())
                .map(this::publish)
                .<FinaliseResult>map(FinaliseResult.Final::new)
                .orElseGet(() -> current(txnId));
        case PostingOutcome.Unknown unknown -> {
          switch (unknown.reason()) {
            case POSTING_CONFLICT -> LOG.error(ApplicationConstants.ALERT_POSTING_CONFLICT);
            case LEDGER_ERROR -> LOG.error(ApplicationConstants.ALERT_LEDGER_ERROR);
            case LEDGER_TIMEOUT, OVERLOADED ->
                LOG.warn(ApplicationConstants.LOG_LEDGER_UNKNOWN, unknown.reason());
          }

          txns.scheduleRecheck(txnId, recheckDelay);
          yield new FinaliseResult.InDoubt();
        }
      };
    } catch (DataAccessException | TransactionException e) {
      // F8: money may have moved; the row stays INITIATED and repair finalises it once PostgreSQL is back.
      LOG.warn(ApplicationConstants.LOG_FINALISE_FAILED);
      return new FinaliseResult.InDoubt();
    }
  }

  /**
   * Hands the event of a committed final row to the asynchronous publisher.
   */
  @Override
  public SendMoneyTxn publish(SendMoneyTxn txn) {
    events.publish(eventMapper.toEvent(txn));
    return txn;
  }

  /**
   * The CAS found the row already final (another instance or the repair worker won the race):
   * report its state.
   */
  private FinaliseResult current(UUID txnId) {
    LOG.info(ApplicationConstants.LOG_ALREADY_FINALISED);
    return txns.findById(txnId)
        .filter(txn -> txn.status().isFinal())
        .<FinaliseResult>map(FinaliseResult.Final::new)
        .orElseGet(FinaliseResult.InDoubt::new);
  }
}
