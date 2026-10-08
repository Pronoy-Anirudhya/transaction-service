package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.command.QuoteCommand;
import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.fakes.InMemoryRuleRepository;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Op;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Recheck;
import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository.Release;
import com.bracits.transactionservice.application.fakes.SendMoneyHarness;
import com.bracits.transactionservice.application.quote.QuoteClaims;
import com.bracits.transactionservice.application.result.QuoteResult;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.result.SendMoneyResult.Completed;
import com.bracits.transactionservice.application.result.SendMoneyResult.InvalidQuoteToken;
import com.bracits.transactionservice.application.result.SendMoneyResult.Processing;
import com.bracits.transactionservice.application.result.SendMoneyResult.Rejected;
import com.bracits.transactionservice.config.LogConstants;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Pricing;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.event.EventType;
import com.bracits.transactionservice.domain.event.SendMoneyEvent;
import com.bracits.transactionservice.domain.ledger.Leg;
import com.bracits.transactionservice.domain.ledger.LegCode;
import com.bracits.transactionservice.domain.ledger.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.PostingRequest;
import com.bracits.transactionservice.domain.ledger.UnknownReason;
import com.bracits.transactionservice.domain.limit.LimitReservation;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import com.bracits.transactionservice.domain.wallet.WalletType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.resilience.InvocationRejectedException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static com.bracits.transactionservice.application.fakes.Fixtures.AMOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.BUSINESS_DATE;
import static com.bracits.transactionservice.application.fakes.Fixtures.CURRENCY;
import static com.bracits.transactionservice.application.fakes.Fixtures.FREE_AMOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.KEY;
import static com.bracits.transactionservice.application.fakes.Fixtures.LEDGER_TS;
import static com.bracits.transactionservice.application.fakes.Fixtures.NOW;
import static com.bracits.transactionservice.application.fakes.Fixtures.RECEIVER_ACCOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.RECEIVER_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_ACCOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_ID;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.STANDARD_PRICING;
import static com.bracits.transactionservice.application.fakes.Fixtures.SYSTEM_ACCOUNTS;
import static com.bracits.transactionservice.application.fakes.Fixtures.UNKNOWN_MSISDN;
import static com.bracits.transactionservice.application.fakes.Fixtures.command;
import static com.bracits.transactionservice.application.fakes.Fixtures.receiver;
import static com.bracits.transactionservice.application.fakes.Fixtures.sender;
import static com.bracits.transactionservice.application.fakes.Fixtures.withReference;
import static com.bracits.transactionservice.application.fakes.Fixtures.withStatus;
import static com.bracits.transactionservice.application.fakes.Fixtures.withToken;
import static com.bracits.transactionservice.application.fakes.Fixtures.withType;
import static org.assertj.core.api.Assertions.assertThat;

class SendMoneyServiceTest {

  private static final PostingOutcome POSTED = new PostingOutcome.Posted(LEDGER_TS, false);
  private static final PostingOutcome INSUFFICIENT = new PostingOutcome.Rejected(FailureCode.INSUFFICIENT_FUNDS, 1);
  private static final PostingOutcome TIMEOUT = new PostingOutcome.Unknown(UnknownReason.LEDGER_TIMEOUT);

  private final SendMoneyHarness h = new SendMoneyHarness();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  private SendMoneyResult send(SendMoneyCommand command) {
    return h.service.send(command);
  }

  private static UUID txnIdOf(SendMoneyResult result) {
    return switch (result) {
      case Completed c -> c.txn().txnId();
      case Processing p -> p.txn().txnId();
      case Rejected r -> r.txnId().orElseThrow();
      case InvalidQuoteToken i -> throw new AssertionError("no txnId");
    };
  }

  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class HappyPath {

    @Test
    void postsOnceWithFourLegsAndCompletes() {
      h.ledger.thenReturn(POSTED);

      SendMoneyResult result = send(command());

      assertThat(result).isInstanceOfSatisfying(Completed.class, c -> {
        assertThat(c.txn().status()).isEqualTo(TxnStatus.COMPLETED);
        assertThat(c.txn().amount()).isEqualTo(AMOUNT);
        assertThat(c.txn().pricing()).isEqualTo(STANDARD_PRICING);
        assertThat(c.txn().totalDebit()).isEqualTo(100_500L);
        assertThat(c.txn().failureCode()).isEmpty();
        assertThat(c.txn().completedAt()).contains(NOW);
      });
      UUID txnId = txnIdOf(result);
      assertThat(h.ids.issued()).containsExactly(txnId);
      assertThat(h.ledger.requests()).singleElement().satisfies(request -> {
        assertThat(request.postingId()).isEqualTo(txnId);
        assertThat(request.userData64()).isEqualTo(SENDER_ID);
        assertThat(request.legs()).containsExactly(
            new Leg(SENDER_ACCOUNT, RECEIVER_ACCOUNT, AMOUNT, LegCode.PRINCIPAL),
            new Leg(SENDER_ACCOUNT, SYSTEM_ACCOUNTS.feeIncome(), 348L, LegCode.FEE),
            new Leg(SENDER_ACCOUNT, SYSTEM_ACCOUNTS.vatPayable(), 65L, LegCode.VAT),
            new Leg(SENDER_ACCOUNT, SYSTEM_ACCOUNTS.commissionPayable(), 87L, LegCode.COMMISSION));
      });
    }

    @Test
    void storesTheRowAndReservesLimitsForTheDhakaBusinessDate() {
      h.ledger.thenReturn(POSTED);

      UUID txnId = txnIdOf(send(command()));

      SendMoneyTxn row = h.txns.get(txnId);
      assertThat(row.status()).isEqualTo(TxnStatus.COMPLETED);
      assertThat(row.ledgerTimestamp()).hasValue(LEDGER_TS);
      assertThat(row.clientRef()).isEqualTo(KEY);
      assertThat(row.senderWalletId()).isEqualTo(SENDER_ID);
      assertThat(row.reference()).contains("rent");
      assertThat(row.currency()).isEqualTo(CURRENCY);
      assertThat(row.businessDate()).isEqualTo(BUSINESS_DATE);
      assertThat(row.requestHash()).isEqualTo(new RequestHasher().hash(command()));
      assertThat(h.limits.calls()).containsExactly(
          new LimitReservation(SENDER_ID, BUSINESS_DATE, AMOUNT, InMemoryRuleRepository.TIER_1_LIMIT));
      assertThat(h.transactions.commits()).isEqualTo(1);
      assertThat(h.transactions.rollbacks()).isZero();
    }

    @Test
    void publishesTheCompletedEventOnceAfterTheRowIsCommitted() {
      h.ledger.thenReturn(POSTED);

      UUID txnId = txnIdOf(send(command()));

      assertThat(h.events.events()).singleElement().satisfies(event -> {
        assertThat(event.eventType()).isEqualTo(EventType.SEND_MONEY_COMPLETED);
        assertThat(event.txnId()).isEqualTo(txnId);
        assertThat(event.ledgerTimestamp()).hasValue(LEDGER_TS);
        assertThat(event.failureCode()).isEmpty();
      });
      assertThat(h.events.statusesAtPublish()).containsExactly(Optional.of(TxnStatus.COMPLETED));
    }

    @Test
    void freeSlabPostsOnlyThePrincipalLeg() {
      h.ledger.thenReturn(POSTED);

      SendMoneyResult result = send(command(KEY, FREE_AMOUNT));

      assertThat(result).isInstanceOfSatisfying(Completed.class,
          c -> assertThat(c.txn().pricing()).isEqualTo(Pricing.FREE));
      assertThat(h.ledger.requests()).singleElement()
          .satisfies(r -> assertThat(r.legs()).extracting(Leg::code).containsExactly(LegCode.PRINCIPAL));
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class RuleFailures {

    private void assertRejectedWithoutWrites(SendMoneyResult result, FailureCode code) {
      assertThat(result).isEqualTo(new Rejected(code, Optional.empty()));
      assertThat(h.txns.insertAttempts()).isEmpty();
      assertThat(h.txns.size()).isZero();
      assertThat(h.ids.issued()).isEmpty();
      assertThat(h.limits.calls()).isEmpty();
      assertThat(h.ledger.requests()).isEmpty();
      assertThat(h.events.events()).isEmpty();
    }

    @Test
    void selfTransfer() {
      assertRejectedWithoutWrites(send(command(KEY, SENDER_MSISDN, SENDER_MSISDN, AMOUNT)), FailureCode.SELF_TRANSFER);
    }

    @Test
    void unknownReceiver() {
      assertRejectedWithoutWrites(send(command(KEY, SENDER_MSISDN, UNKNOWN_MSISDN, AMOUNT)),
          FailureCode.WALLET_NOT_FOUND);
    }

    @Test
    void unknownSender() {
      assertRejectedWithoutWrites(send(command(KEY, UNKNOWN_MSISDN, RECEIVER_MSISDN, AMOUNT)),
          FailureCode.WALLET_NOT_FOUND);
    }

    @Test
    void inactiveSender() {
      h.wallets.put(withStatus(sender(), WalletStatus.FROZEN));

      assertRejectedWithoutWrites(send(command()), FailureCode.WALLET_INACTIVE);
    }

    @Test
    void inactiveReceiver() {
      h.wallets.put(withStatus(receiver(), WalletStatus.CLOSED));

      assertRejectedWithoutWrites(send(command()), FailureCode.WALLET_INACTIVE);
    }

    @Test
    void nonCustomerReceiver() {
      h.wallets.put(withType(receiver(), WalletType.SYSTEM));

      assertRejectedWithoutWrites(send(command()), FailureCode.RECEIVER_NOT_ALLOWED);
    }

    @Test
    void amountBelowTheTierMinimum() {
      assertRejectedWithoutWrites(send(command(KEY, 999L)), FailureCode.AMOUNT_OUT_OF_RANGE);
    }

    @Test
    void amountAboveTheTierMaximum() {
      assertRejectedWithoutWrites(send(command(KEY, 2_500_001L)), FailureCode.AMOUNT_OUT_OF_RANGE);
    }

    @Test
    void noFeeRuleForTheAmount() {
      h.rules.setFeeRules(List.of());

      assertRejectedWithoutWrites(send(command()), FailureCode.AMOUNT_OUT_OF_RANGE);
    }

    @Test
    void replayOfAnAcceptedRequestReturnsTheStoredOutcomeEvenAfterTheWalletBecameInactive() {
      h.ledger.thenReturn(POSTED);
      SendMoneyResult first = send(command());
      h.wallets.put(withStatus(sender(), WalletStatus.FROZEN));

      SendMoneyResult replay = send(command());

      assertThat(first).isInstanceOf(Completed.class);
      assertThat(replay).isEqualTo(first);
      assertThat(h.ledger.requests()).hasSize(1);
      assertThat(h.events.events()).hasSize(1);
      assertThat(h.txns.size()).isEqualTo(1);
    }

    @Test
    void replayOfAFailedRequestAfterTheAmountRuleChangedReturnsTheStoredFailure() {
      h.ledger.thenReturn(INSUFFICIENT);
      SendMoneyResult first = send(command());
      h.rules.setLimitRules(List.of());

      assertThat(send(command())).isEqualTo(first);
    }

    @Test
    void differentBodyWithTheSameKeyAfterTheWalletBecameInactiveIsAnIdempotencyConflict() {
      h.ledger.thenReturn(POSTED);
      send(command());
      h.wallets.put(withStatus(sender(), WalletStatus.FROZEN));

      assertThat(send(command(KEY, AMOUNT + 1)))
          .isEqualTo(new Rejected(FailureCode.IDEMPOTENCY_CONFLICT, Optional.empty()));
    }

    @Test
    void failedReplayLookupFallsBackToTheRejection() {
      h.wallets.put(withStatus(sender(), WalletStatus.FROZEN));
      h.txns.failNext(Op.FIND_BY_SENDER, new DataAccessResourceFailureException("db down"));

      assertThat(send(command())).isEqualTo(new Rejected(FailureCode.WALLET_INACTIVE, Optional.empty()));
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class Limits {

    @Test
    void limitExceededRollsBackTheInsertAndNeverCallsTheLedger() {
      h.limits.reject();

      SendMoneyResult result = send(command());

      assertThat(result).isEqualTo(new Rejected(FailureCode.LIMIT_EXCEEDED, Optional.empty()));
      assertThat(h.txns.insertAttempts()).hasSize(1);
      assertThat(h.txns.size()).isZero();
      assertThat(h.limits.calls()).hasSize(1);
      assertThat(h.limits.rollbacks()).isEqualTo(1);
      assertThat(h.transactions.commits()).isZero();
      assertThat(h.ledger.requests()).isEmpty();
      assertThat(h.events.events()).isEmpty();
      assertThat(h.counter("limit.rejections")).isEqualTo(1.0);
    }

    @Test
    void theSameKeyIsFreeAgainAfterALimitRejection() {
      h.limits.reject();
      send(command());
      h.limits.accept();
      h.ledger.thenReturn(POSTED);

      assertThat(send(command())).isInstanceOf(Completed.class);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class Duplicates {

    @Test
    void sameKeySameBodyOfACompletedRowReturnsTheStoredResult() {
      h.ledger.thenReturn(POSTED);
      SendMoneyResult first = send(command());

      SendMoneyResult second = send(command());

      assertThat(second).isEqualTo(first);
      assertThat(h.ledger.requests()).hasSize(1);
      assertThat(h.limits.calls()).hasSize(1);
      assertThat(h.events.events()).hasSize(1);
      assertThat(h.txns.size()).isEqualTo(1);
    }

    @Test
    void sameKeySameBodyOfAFailedRowReturnsTheFailureWithTheTxnId() {
      h.ledger.thenReturn(INSUFFICIENT);
      SendMoneyResult first = send(command());

      SendMoneyResult second = send(command());

      assertThat(second).isEqualTo(new Rejected(FailureCode.INSUFFICIENT_FUNDS, Optional.of(txnIdOf(first))));
      assertThat(h.ledger.requests()).hasSize(1);
      assertThat(h.events.events()).hasSize(1);
    }

    @Test
    void sameKeySameBodyOfAnInitiatedRowIsStillProcessing() {
      h.ledger.thenReturn(TIMEOUT);
      SendMoneyResult first = send(command());

      SendMoneyResult second = send(command());

      assertThat(second).isInstanceOfSatisfying(Processing.class, p -> {
        assertThat(p.txn().txnId()).isEqualTo(txnIdOf(first));
        assertThat(p.txn().status()).isEqualTo(TxnStatus.INITIATED);
      });
      assertThat(second).isEqualTo(first);
      assertThat(h.ledger.requests()).hasSize(1);
    }

    @Test
    void sameKeyDifferentAmountIsAnIdempotencyConflict() {
      h.ledger.thenReturn(POSTED);
      send(command());

      assertThat(send(command(KEY, AMOUNT + 100)))
          .isEqualTo(new Rejected(FailureCode.IDEMPOTENCY_CONFLICT, Optional.empty()));
      assertThat(h.ledger.requests()).hasSize(1);
      assertThat(h.limits.calls()).hasSize(1);
    }

    @Test
    void sameKeyDifferentReferenceIsAnIdempotencyConflict() {
      h.ledger.thenReturn(POSTED);
      send(command());

      assertThat(send(withReference(command(), Optional.empty())))
          .isEqualTo(new Rejected(FailureCode.IDEMPOTENCY_CONFLICT, Optional.empty()));
    }

    @Test
    void theSameKeyFromAnotherSenderIsANewTransaction() {
      h.ledger.thenReturn(POSTED).thenReturn(POSTED);

      SendMoneyResult first = send(command());
      SendMoneyResult second = send(command(KEY, RECEIVER_MSISDN, SENDER_MSISDN, AMOUNT));

      assertThat(second).isInstanceOf(Completed.class);
      assertThat(txnIdOf(second)).isNotEqualTo(txnIdOf(first));
      assertThat(h.txns.size()).isEqualTo(2);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class LedgerOutcomes {

    @Test
    void definitiveRejectionFailsTheRowReleasesLimitsAndPublishesTheFailedEvent() {
      h.ledger.thenReturn(INSUFFICIENT);

      SendMoneyResult result = send(command());

      UUID txnId = h.ids.issued().getFirst();
      assertThat(result).isEqualTo(new Rejected(FailureCode.INSUFFICIENT_FUNDS, Optional.of(txnId)));
      SendMoneyTxn row = h.txns.get(txnId);
      assertThat(row.status()).isEqualTo(TxnStatus.FAILED);
      assertThat(row.failureCode()).contains(FailureCode.INSUFFICIENT_FUNDS);
      assertThat(h.txns.releases()).containsExactly(new Release(txnId, FailureCode.INSUFFICIENT_FUNDS));
      assertThat(h.events.events()).singleElement().satisfies(event -> {
        assertThat(event.eventType()).isEqualTo(EventType.SEND_MONEY_FAILED);
        assertThat(event.failureCode()).contains(FailureCode.INSUFFICIENT_FUNDS);
      });
      assertThat(h.events.statusesAtPublish()).containsExactly(Optional.of(TxnStatus.FAILED));
    }

    @Test
    void unknownOutcomeIsProcessingWithARecheckAndNoEvent() {
      h.ledger.thenReturn(TIMEOUT);

      SendMoneyResult result = send(command());

      UUID txnId = h.ids.issued().getFirst();
      assertThat(result).isInstanceOfSatisfying(Processing.class, p -> {
        assertThat(p.txn().txnId()).isEqualTo(txnId);
        assertThat(p.txn().status()).isEqualTo(TxnStatus.INITIATED);
        assertThat(p.txn().pricing()).isEqualTo(STANDARD_PRICING);
        assertThat(p.txn().completedAt()).isEmpty();
      });
      assertThat(h.txns.rechecks()).containsExactly(new Recheck(txnId, Duration.ofSeconds(2)));
      SendMoneyTxn row = h.txns.get(txnId);
      assertThat(row.status()).isEqualTo(TxnStatus.INITIATED);
      assertThat(row.ledgerAttempts()).isEqualTo(1);
      assertThat(row.nextCheckAt()).contains(NOW.plusSeconds(2));
      assertThat(h.txns.releases()).isEmpty();
      assertThat(h.events.events()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(UnknownReason.class)
    void everyUnknownReasonLeavesTheRowInitiated(UnknownReason reason) {
      h.ledger.thenReturn(new PostingOutcome.Unknown(reason));

      assertThat(send(command())).isInstanceOf(Processing.class);
      assertThat(h.txns.all()).singleElement().extracting(SendMoneyTxn::status).isEqualTo(TxnStatus.INITIATED);
      assertThat(h.events.events()).isEmpty();
    }

    @Test
    void saturatedLedgerBulkheadAfterTheInsertIsProcessing() {
      h.ledger.thenThrow(new InvocationRejectedException("ledger bulkhead full", new Object()));

      SendMoneyResult result = send(command());

      UUID txnId = h.ids.issued().getFirst();
      assertThat(result).isInstanceOf(Processing.class);
      assertThat(h.txns.get(txnId).status()).isEqualTo(TxnStatus.INITIATED);
      assertThat(h.txns.rechecks()).containsExactly(new Recheck(txnId, Duration.ofSeconds(2)));
      assertThat(h.events.events()).isEmpty();
    }

    @Test
    void unexpectedLedgerClientErrorIsProcessing() {
      h.ledger.thenThrow(new IllegalStateException("boom"));

      assertThat(send(command())).isInstanceOf(Processing.class);
      assertThat(h.txns.all()).singleElement().extracting(SendMoneyTxn::status).isEqualTo(TxnStatus.INITIATED);
    }

    @Test
    void lostCompareAndSetReturnsTheStateTheWinnerStored() {
      h.ledger.thenAnswer(request -> {
        // The repair worker finalises the row while this request is still waiting for the ledger.
        h.txns.markCompleted(request.postingId(), 77L);
        return POSTED;
      });

      SendMoneyResult result = send(command());

      UUID txnId = h.ids.issued().getFirst();
      assertThat(result).isInstanceOfSatisfying(Completed.class,
          c -> assertThat(c.txn().txnId()).isEqualTo(txnId));
      assertThat(h.txns.get(txnId).ledgerTimestamp()).isEqualTo(OptionalLong.of(77L));
      assertThat(h.events.events()).as("the winner publishes, not the loser").isEmpty();
    }

    @Test
    void lostCompareAndSetOnARejectionReturnsTheStoredFailure() {
      h.ledger.thenAnswer(request -> {
        h.txns.markFailedAndReleaseLimits(request.postingId(), FailureCode.INSUFFICIENT_FUNDS);
        return INSUFFICIENT;
      });

      SendMoneyResult result = send(command());

      UUID txnId = h.ids.issued().getFirst();
      assertThat(result).isEqualTo(new Rejected(FailureCode.INSUFFICIENT_FUNDS, Optional.of(txnId)));
      assertThat(h.txns.releases()).hasSize(1);
      assertThat(h.events.events()).isEmpty();
    }

    @Test
    void postgresDownAtFinalisationIsProcessingAndTheRowStaysInitiated() {
      h.ledger.thenReturn(POSTED);
      h.txns.failNext(Op.MARK_COMPLETED, new DataAccessResourceFailureException("db down"));

      SendMoneyResult result = send(command());

      UUID txnId = h.ids.issued().getFirst();
      assertThat(result).isInstanceOfSatisfying(Processing.class, p -> {
        assertThat(p.txn().txnId()).isEqualTo(txnId);
        assertThat(p.txn().status()).isEqualTo(TxnStatus.INITIATED);
      });
      assertThat(h.txns.get(txnId).status()).isEqualTo(TxnStatus.INITIATED);
      assertThat(h.events.events()).isEmpty();
    }

    @Test
    void postgresDownWhileMarkingFailedIsProcessing() {
      h.ledger.thenReturn(INSUFFICIENT);
      h.txns.failNext(Op.MARK_FAILED, new QueryTimeoutException("slow"));

      assertThat(send(command())).isInstanceOf(Processing.class);
      assertThat(h.txns.releases()).isEmpty();
      assertThat(h.events.events()).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class QuoteTokens {

    private String quoteToken() {
      QuoteResult quote = h.quotes.quote(new QuoteCommand(SENDER_MSISDN, RECEIVER_MSISDN, AMOUNT, CURRENCY));
      return ((QuoteResult.Quoted) quote).quoteToken();
    }

    private void assertNothingWritten() {
      assertThat(h.txns.insertAttempts()).isEmpty();
      assertThat(h.ledger.requests()).isEmpty();
      assertThat(h.limits.calls()).isEmpty();
    }

    @Test
    void validTokenCompletes() {
      h.ledger.thenReturn(POSTED);

      assertThat(send(withToken(command(), quoteToken()))).isInstanceOf(Completed.class);
    }

    @Test
    void tamperedTokenIsInvalid() {
      String genuine = quoteToken();
      String otherPayload = h.quoteTokens.encode(
          new QuoteClaims(SENDER_MSISDN, RECEIVER_MSISDN, 1_000L, 0L, NOW.plusSeconds(300))).split("\\.")[0];
      String tampered = otherPayload + "." + genuine.split("\\.")[1];

      assertThat(send(withToken(command(), tampered))).isEqualTo(new InvalidQuoteToken());
      assertNothingWritten();
    }

    @Test
    void garbageTokenIsInvalid() {
      assertThat(send(withToken(command(), "not-a-token"))).isEqualTo(new InvalidQuoteToken());
      assertNothingWritten();
    }

    @Test
    void expiredTokenIsAQuoteChange() {
      String token = quoteToken();
      h.clock.advance(Duration.ofMinutes(5));

      assertThat(send(withToken(command(), token)))
          .isEqualTo(new Rejected(FailureCode.QUOTE_CHANGED, Optional.empty()));
      assertNothingWritten();
    }

    @Test
    void tokenForAnotherAmountIsAQuoteChange() {
      String token = quoteToken();

      assertThat(send(withToken(command(KEY, AMOUNT + 100), token)))
          .isEqualTo(new Rejected(FailureCode.QUOTE_CHANGED, Optional.empty()));
      assertNothingWritten();
    }

    @Test
    void tokenForAnotherReceiverIsAQuoteChange() {
      String token = h.quoteTokens.encode(
          new QuoteClaims(SENDER_MSISDN, UNKNOWN_MSISDN, AMOUNT, 500L, NOW.plusSeconds(300)));

      assertThat(send(withToken(command(), token)))
          .isEqualTo(new Rejected(FailureCode.QUOTE_CHANGED, Optional.empty()));
    }

    @Test
    void feeChangedSinceTheQuoteIsAQuoteChange() {
      String token = quoteToken();
      h.rules.setFeeRules(InMemoryRuleRepository.standardFeeRules(600L));

      assertThat(send(withToken(command(), token)))
          .isEqualTo(new Rejected(FailureCode.QUOTE_CHANGED, Optional.empty()));
      assertNothingWritten();
    }

    @Test
    void replayAfterTheTokenExpiredReturnsTheStoredOutcome() {
      h.ledger.thenReturn(POSTED);
      SendMoneyCommand command = withToken(command(), quoteToken());
      SendMoneyResult first = send(command);
      h.clock.advance(Duration.ofMinutes(10));

      assertThat(send(command)).isEqualTo(first);
      assertThat(h.ledger.requests()).hasSize(1);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class Observability {

    @Test
    void txnIdIsInTheMdcDuringTheLedgerCallAndClearedAfterwards() {
      h.ledger.thenReturn(POSTED);

      UUID txnId = txnIdOf(send(command()));

      assertThat(h.ledger.mdcTxnIds()).containsExactly(Optional.of(txnId.toString()));
      assertThat(MDC.get(LogConstants.MDC_TXN_ID)).isNull();
    }

    @Test
    void mdcIsClearedAfterAReplayAndAfterARejection() {
      h.ledger.thenReturn(POSTED);
      send(command());
      send(command());
      assertThat(MDC.get(LogConstants.MDC_TXN_ID)).isNull();

      h.limits.reject();
      send(command("other-key", AMOUNT));
      assertThat(MDC.get(LogConstants.MDC_TXN_ID)).isNull();
    }

    @Test
    void mdcIsClearedWhenTheRequestFails() {
      h.txns.failNext(Op.INSERT, new DataAccessResourceFailureException("db down"));

      try {
        send(command());
      } catch (DataAccessResourceFailureException expected) {
        // F7: PostgreSQL down at step 6 → 503, mapped by the API layer.
      }
      assertThat(MDC.get(LogConstants.MDC_TXN_ID)).isNull();
      assertThat(h.ledger.requests()).isEmpty();
    }

    @Test
    void countsEveryOutcomeWithItsCode() {
      h.ledger.thenReturn(POSTED).thenReturn(INSUFFICIENT).thenReturn(TIMEOUT);

      send(command("k1", AMOUNT));
      send(command("k2", AMOUNT));
      send(command("k3", AMOUNT));
      h.limits.reject();
      send(command("k4", AMOUNT));
      send(withToken(command("k5", AMOUNT), "bogus"));
      send(command("k6", SENDER_MSISDN, SENDER_MSISDN, AMOUNT));

      assertThat(h.sendCount("completed", "none")).isEqualTo(1.0);
      assertThat(h.sendCount("rejected", "INSUFFICIENT_FUNDS")).isEqualTo(1.0);
      assertThat(h.sendCount("processing", "none")).isEqualTo(1.0);
      assertThat(h.sendCount("rejected", "LIMIT_EXCEEDED")).isEqualTo(1.0);
      assertThat(h.sendCount("invalid", "none")).isEqualTo(1.0);
      assertThat(h.sendCount("rejected", "SELF_TRANSFER")).isEqualTo(1.0);
      assertThat(h.counter("limit.rejections")).isEqualTo(1.0);
      assertThat(h.registry.find("sendmoney.duration").tag("outcome", "rejected").timer())
          .isNotNull()
          .satisfies(timer -> assertThat(timer.count()).isEqualTo(3L));
    }
  }

  @Test
  void eventCarriesTheRowsAmountsAndPricing() {
    h.ledger.thenReturn(POSTED);

    send(command());

    SendMoneyEvent event = h.events.events().getFirst();
    assertThat(event.amount()).isEqualTo(AMOUNT);
    assertThat(event.fee()).isEqualTo(500L);
    assertThat(event.vat()).isEqualTo(65L);
    assertThat(event.commission()).isEqualTo(87L);
    assertThat(event.feeIncome()).isEqualTo(348L);
    assertThat(event.occurredAt()).isEqualTo(NOW);
  }

  @Test
  void ledgerRequestIsTheLegPlannersPlanForTheRow() {
    h.ledger.thenReturn(POSTED);

    UUID txnId = txnIdOf(send(command()));

    PostingRequest expected = h.legPlanner.plan(txnId, sender(), receiver(), AMOUNT, STANDARD_PRICING);
    assertThat(h.ledger.requests()).containsExactly(expected);
  }
}
