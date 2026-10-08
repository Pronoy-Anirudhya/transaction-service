package com.bracits.transactionservice.application.sendmoney.service.impl;

import com.bracits.transactionservice.application.calendar.service.BusinessCalendar;
import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.enums.ReservationOutcome;
import com.bracits.transactionservice.application.mapper.TxnMapper;
import com.bracits.transactionservice.application.metrics.service.SendMoneyMetrics;
import com.bracits.transactionservice.application.posting.service.LedgerPoster;
import com.bracits.transactionservice.application.posting.service.TxnFinaliser;
import com.bracits.transactionservice.application.result.FinaliseResult;
import com.bracits.transactionservice.application.result.PreparationResult;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.sendmoney.service.IdempotentReplay;
import com.bracits.transactionservice.application.sendmoney.service.LimitReserver;
import com.bracits.transactionservice.application.sendmoney.service.QuoteVerifier;
import com.bracits.transactionservice.application.sendmoney.service.RequestHasher;
import com.bracits.transactionservice.application.sendmoney.service.SendMoneyPreparation;
import com.bracits.transactionservice.application.sendmoney.service.SendMoneyService;
import com.bracits.transactionservice.config.constant.LogConstants;
import com.bracits.transactionservice.config.constant.PropertyConstants;
import com.bracits.transactionservice.config.properties.RepairProperties;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.planner.LegPlanner;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.port.out.client.LedgerHealthPort;
import com.bracits.transactionservice.port.out.generator.TxnIdGenerator;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.ProxyType;
import org.springframework.context.annotation.Proxyable;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link SendMoneyService}.
 *
 * <p>Not final and pinned to a class-based proxy: {@code @ConcurrencyLimit} on {@link #send} is
 * resolved from the invoked method, which a JDK interface proxy would report as the unannotated
 * {@link SendMoneyService#send} (decision W1).
 */
@Service
@Proxyable(ProxyType.TARGET_CLASS)
public class SendMoneyServiceImpl implements SendMoneyService {

  private static final Logger LOG = LoggerFactory.getLogger(SendMoneyServiceImpl.class);

  private final SendMoneyPreparation preparation;
  private final RequestHasher hasher;
  private final QuoteVerifier quoteVerifier;
  private final IdempotentReplay replay;
  private final TxnIdGenerator ids;
  private final BusinessCalendar calendar;
  private final LimitReserver limitReserver;
  private final LegPlanner legPlanner;
  private final LedgerPoster ledger;
  private final TxnFinaliser finaliser;
  private final TxnMapper txnMapper;
  private final SendMoneyMetrics metrics;
  private final RepairProperties repair;
  private final LedgerHealthPort ledgerHealth;

  public SendMoneyServiceImpl(
      SendMoneyPreparation preparation,
      RequestHasher hasher,
      QuoteVerifier quoteVerifier,
      IdempotentReplay replay,
      TxnIdGenerator ids,
      BusinessCalendar calendar,
      LimitReserver limitReserver,
      LegPlanner legPlanner,
      LedgerPoster ledger,
      TxnFinaliser finaliser,
      TxnMapper txnMapper,
      SendMoneyMetrics metrics,
      RepairProperties repair,
      LedgerHealthPort ledgerHealth) {
    this.preparation = preparation;
    this.hasher = hasher;
    this.quoteVerifier = quoteVerifier;
    this.replay = replay;
    this.ids = ids;
    this.calendar = calendar;
    this.limitReserver = limitReserver;
    this.legPlanner = legPlanner;
    this.ledger = ledger;
    this.finaliser = finaliser;
    this.txnMapper = txnMapper;
    this.metrics = metrics;
    this.repair = repair;
    this.ledgerHealth = ledgerHealth;
  }

  /**
   * Bulkhead on the posting endpoint (P10): when saturated, rejected before any write → 503 +
   * Retry-After.
   */
  @ConcurrencyLimit(
      limitString = PropertyConstants.SEND_MONEY_CONCURRENCY_LIMIT_PLACEHOLDER,
      policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
  @Override
  public SendMoneyResult send(SendMoneyCommand command) {
    long start = System.nanoTime();

    try {
      SendMoneyResult result = execute(command);

      Duration duration = Duration.ofNanos(System.nanoTime() - start);
      metrics.recordSend(result, duration);
      LOG.info(ApplicationConstants.LOG_SEND_OUTCOME,
          SendMoneyMetrics.outcomeOf(result), SendMoneyMetrics.codeOf(result), duration.toMillis());
      return result;
    } finally {
      MDC.remove(LogConstants.MDC_TXN_ID);
    }
  }

  /**
   * Steps 1–4, all in memory: a failure writes nothing, but may still be a replay of an accepted
   * request.
   */
  private SendMoneyResult execute(SendMoneyCommand command) {
    RequestHash hash = hasher.hash(command);

    PreparationResult prepared =
        preparation.prepare(command.senderMsisdn(), command.receiverMsisdn(), command.amount());
    if (prepared instanceof PreparationResult.Failed failed) {
      return replay.replayOr(failed.sender(), command.idempotencyKey(), hash,
          () -> new SendMoneyResult.Rejected(failed.code(), Optional.empty()));
    }
    PreparationResult.Ready ready = (PreparationResult.Ready) prepared;

    Optional<SendMoneyResult> quoteProblem = quoteVerifier.verify(command, ready.pricing());
    if (quoteProblem.isPresent()) {
      return replay.replayOr(Optional.of(ready.sender()), command.idempotencyKey(), hash,
          quoteProblem::get);
    }

    if (!ledgerHealth.isAvailable()) {
      // Fail fast before any write: no row, no limit reservation, nothing to repair later.
      return replay.replayOr(Optional.of(ready.sender()), command.idempotencyKey(), hash,
          SendMoneyResult.LedgerUnavailable::new);
    }

    return reserveAndPost(command, hash, ready);
  }

  /**
   * Steps 5–6: txnId, then DB transaction #1 — the row is durable before the ledger is called.
   */
  private SendMoneyResult reserveAndPost(SendMoneyCommand command, RequestHash hash,
      PreparationResult.Ready ready) {
    UUID txnId = ids.next();
    MDC.put(LogConstants.MDC_TXN_ID, txnId.toString());
    NewSendMoneyTxn txn = txnMapper.toNewTxn(
        txnId, command, hash, ready.sender(), ready.receiver(), ready.pricing(), calendar.today());

    ReservationOutcome reservation =
        limitReserver.reserve(txn, txnMapper.toLimitReservation(txn, ready.limitRule()));
    return switch (reservation) {
      case DUPLICATE -> replay.replayExisting(txn.senderWalletId(), txn.clientRef(), hash);
      case LIMIT_EXCEEDED -> limitExceeded();
      case RESERVED -> postAndFinalise(txn, ready);
    };
  }

  /**
   * Steps 7–10: one ledger call outside any DB transaction (P7), then compare-and-set
   * finalisation.
   */
  private SendMoneyResult postAndFinalise(NewSendMoneyTxn txn, PreparationResult.Ready ready) {
    PostingOutcome outcome = ledger.post(
        legPlanner.plan(txn.txnId(), ready.sender(), ready.receiver(), txn.amount(),
            ready.pricing()));

    return switch (finaliser.finalise(txn.txnId(), outcome, repair.unknownRecheck())) {
      case FinaliseResult.Final(SendMoneyTxn row) -> txnMapper.toResult(row);
      case FinaliseResult.InDoubt() ->
          new SendMoneyResult.Processing(txnMapper.toInitiatedSummary(txn));
    };
  }

  private SendMoneyResult limitExceeded() {
    metrics.limitRejected();
    return new SendMoneyResult.Rejected(FailureCode.LIMIT_EXCEEDED, Optional.empty());
  }
}
