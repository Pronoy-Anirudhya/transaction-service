package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.SendMoneyPreparation.Prepared;
import com.bracits.transactionservice.application.TxnFinaliser.Finalised;
import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.mapper.TxnMapper;
import com.bracits.transactionservice.application.quote.QuoteTokenCheck;
import com.bracits.transactionservice.application.quote.QuoteTokenCodec;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.config.BusinessProperties;
import com.bracits.transactionservice.config.LogConstants;
import com.bracits.transactionservice.config.PropertyConstants;
import com.bracits.transactionservice.config.RepairProperties;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.ledger.LegPlanner;
import com.bracits.transactionservice.domain.ledger.PostingOutcome;
import com.bracits.transactionservice.domain.limit.LimitReservation;
import com.bracits.transactionservice.domain.txn.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.RequestHash;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.port.out.LimitRepository;
import com.bracits.transactionservice.port.out.TxnIdGenerator;
import com.bracits.transactionservice.port.out.TxnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * FR-02 Send Money, exactly spec 5: in-memory validation and pricing → DB transaction #1 (insert + conditional limit
 * update) → ONE ledger call outside any DB transaction → compare-and-set finalisation → 200 / 422 / 202. The event is
 * published asynchronously after the final commit.
 */
@Service
public class SendMoneyService {

  private static final Logger LOG = LoggerFactory.getLogger(SendMoneyService.class);

  private final SendMoneyPreparation preparation;
  private final RequestHasher hasher;
  private final QuoteTokenCodec quoteTokens;
  private final TxnIdGenerator ids;
  private final TxnRepository txns;
  private final LimitRepository limits;
  private final TransactionOperations transactions;
  private final LegPlanner legPlanner;
  private final LedgerPoster ledger;
  private final TxnFinaliser finaliser;
  private final TxnMapper txnMapper;
  private final SendMoneyMetrics metrics;
  private final BusinessProperties business;
  private final RepairProperties repair;
  private final Clock clock;

  public SendMoneyService(
      SendMoneyPreparation preparation,
      RequestHasher hasher,
      QuoteTokenCodec quoteTokens,
      TxnIdGenerator ids,
      TxnRepository txns,
      LimitRepository limits,
      TransactionOperations transactions,
      LegPlanner legPlanner,
      LedgerPoster ledger,
      TxnFinaliser finaliser,
      TxnMapper txnMapper,
      SendMoneyMetrics metrics,
      BusinessProperties business,
      RepairProperties repair,
      Clock clock) {
    this.preparation = preparation;
    this.hasher = hasher;
    this.quoteTokens = quoteTokens;
    this.ids = ids;
    this.txns = txns;
    this.limits = limits;
    this.transactions = transactions;
    this.legPlanner = legPlanner;
    this.ledger = ledger;
    this.finaliser = finaliser;
    this.txnMapper = txnMapper;
    this.metrics = metrics;
    this.business = business;
    this.repair = repair;
    this.clock = clock;
  }

  /** Bulkhead on the posting endpoint (P10): when saturated, rejected before any write → 503 + Retry-After. */
  @ConcurrencyLimit(
      limitString = PropertyConstants.SEND_MONEY_CONCURRENCY_LIMIT_PLACEHOLDER,
      policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
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

  private SendMoneyResult execute(SendMoneyCommand command) {
    RequestHash hash = hasher.hash(command);
    // Steps 2–4: wallets from the cache, rule chain, pricing — all in memory; a failure writes nothing.
    Prepared prepared = preparation.prepare(command.senderMsisdn(), command.receiverMsisdn(), command.amount());
    if (prepared instanceof Prepared.Failed failed) {
      return replayOr(failed.sender(), command, hash,
          () -> new SendMoneyResult.Rejected(failed.code(), Optional.empty()));
    }
    Prepared.Ready ready = (Prepared.Ready) prepared;
    Optional<SendMoneyResult> quoteProblem = checkQuote(command, ready);
    if (quoteProblem.isPresent()) {
      return replayOr(Optional.of(ready.sender()), command, hash, quoteProblem::get);
    }

    // Step 5: lock-free, time-ordered txnId (spec 6.3).
    UUID txnId = ids.next();
    MDC.put(LogConstants.MDC_TXN_ID, txnId.toString());
    LocalDate businessDate = LocalDate.now(clock.withZone(business.zone()));
    NewSendMoneyTxn txn = txnMapper.toNewTxn(
        txnId, command, hash, ready.sender(), ready.receiver(), ready.pricing(), businessDate);

    // Step 6: DB transaction #1 — the row is durable before the ledger is called.
    Reservation reservation = reserve(txn,
        new LimitReservation(ready.sender().walletId(), businessDate, command.amount(), ready.limitRule()));
    switch (reservation) {
      case DUPLICATE -> {
        return replayExisting(ready.sender().walletId(), command.idempotencyKey(), hash);
      }
      case LIMIT_EXCEEDED -> {
        metrics.limitRejected();
        return new SendMoneyResult.Rejected(FailureCode.LIMIT_EXCEEDED, Optional.empty());
      }
      case RESERVED -> {
        // continue with the ledger call
      }
    }

    // Step 7: one ledger call, outside any DB transaction (P7).
    PostingOutcome outcome = ledger.post(
        legPlanner.plan(txnId, ready.sender(), ready.receiver(), command.amount(), ready.pricing()));

    // Steps 8–10: compare-and-set finalisation; the event goes out asynchronously after the commit.
    return switch (finaliser.finalise(txnId, outcome, repair.unknownRecheck())) {
      case Finalised.Final(SendMoneyTxn row) -> fromRow(row);
      case Finalised.InDoubt() -> new SendMoneyResult.Processing(txnMapper.toInitiatedSummary(txn));
    };
  }

  /** Insert + conditional limit update in one short transaction; the insert is rolled back if the limit fails. */
  private Reservation reserve(NewSendMoneyTxn txn, LimitReservation reservation) {
    return Objects.requireNonNull(transactions.execute(status -> {
      if (txns.insertIfAbsent(txn).isEmpty()) {
        return Reservation.DUPLICATE;
      }
      if (!limits.reserve(reservation)) {
        status.setRollbackOnly();
        return Reservation.LIMIT_EXCEEDED;
      }
      return Reservation.RESERVED;
    }));
  }

  /** If the token is present it must be genuine, unexpired, for this request, and quote today's fee. */
  private Optional<SendMoneyResult> checkQuote(SendMoneyCommand command, Prepared.Ready ready) {
    if (command.quoteToken().isEmpty()) {
      return Optional.empty();
    }
    return switch (quoteTokens.decode(command.quoteToken().get())) {
      case QuoteTokenCheck.Invalid invalid -> Optional.of(new SendMoneyResult.InvalidQuoteToken());
      case QuoteTokenCheck.Valid valid -> {
        boolean unchanged = valid.claims().matches(
            command.senderMsisdn(), command.receiverMsisdn(), command.amount(), clock.instant())
            && valid.claims().fee() == ready.pricing().fee();
        yield unchanged
            ? Optional.empty()
            : Optional.of(new SendMoneyResult.Rejected(FailureCode.QUOTE_CHANGED, Optional.empty()));
      }
    };
  }

  /**
   * A request rejected before the insert may be a replay of a request that was accepted earlier (FR-03, decision B3):
   * if a row exists for (sender, Idempotency-Key), answer from it; otherwise return the rejection.
   */
  private SendMoneyResult replayOr(
      Optional<Wallet> sender, SendMoneyCommand command, RequestHash hash, Supplier<SendMoneyResult> rejection) {
    if (sender.isEmpty()) {
      return rejection.get();
    }
    try {
      return txns.findBySenderAndClientRef(sender.get().walletId(), command.idempotencyKey())
          .map(row -> replay(row, hash))
          .orElseGet(rejection);
    } catch (DataAccessException e) {
      LOG.warn(ApplicationConstants.LOG_REPLAY_LOOKUP_FAILED);
      return rejection.get();
    }
  }

  private SendMoneyResult replayExisting(long senderWalletId, String idempotencyKey, RequestHash hash) {
    SendMoneyTxn row = txns.findBySenderAndClientRef(senderWalletId, idempotencyKey).orElseThrow();
    return replay(row, hash);
  }

  /** Same key and same body → the stored outcome; same key and a different body → 409. */
  private SendMoneyResult replay(SendMoneyTxn row, RequestHash hash) {
    MDC.put(LogConstants.MDC_TXN_ID, row.txnId().toString());
    if (!row.requestHash().matches(hash)) {
      return new SendMoneyResult.Rejected(FailureCode.IDEMPOTENCY_CONFLICT, Optional.empty());
    }
    return fromRow(row);
  }

  private SendMoneyResult fromRow(SendMoneyTxn row) {
    return switch (row.status()) {
      case INITIATED -> new SendMoneyResult.Processing(txnMapper.toSummary(row));
      case COMPLETED -> new SendMoneyResult.Completed(txnMapper.toSummary(row));
      case FAILED -> new SendMoneyResult.Rejected(
          row.failureCode().orElse(FailureCode.LEDGER_REJECTED), Optional.of(row.txnId()));
    };
  }

  private enum Reservation {
    RESERVED,
    DUPLICATE,
    LIMIT_EXCEEDED
  }
}
