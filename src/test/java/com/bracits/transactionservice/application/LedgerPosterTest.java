package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.fakes.ScriptedLedgerPort;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.ledger.LegPlanner;
import com.bracits.transactionservice.domain.ledger.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.PostingRequest;
import com.bracits.transactionservice.domain.ledger.UnknownReason;
import com.bracits.transactionservice.port.out.LedgerUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.resilience.InvocationRejectedException;

import java.util.UUID;

import static com.bracits.transactionservice.application.fakes.Fixtures.AMOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.LEDGER_TS;
import static com.bracits.transactionservice.application.fakes.Fixtures.STANDARD_PRICING;
import static com.bracits.transactionservice.application.fakes.Fixtures.SYSTEM_ACCOUNTS;
import static com.bracits.transactionservice.application.fakes.Fixtures.receiver;
import static com.bracits.transactionservice.application.fakes.Fixtures.sender;
import static org.assertj.core.api.Assertions.assertThat;

class LedgerPosterTest {

  private static final PostingRequest REQUEST = new LegPlanner(SYSTEM_ACCOUNTS)
      .plan(UUID.fromString("0192f5a4-1111-2222-3333-444444444400"), sender(), receiver(), AMOUNT, STANDARD_PRICING);

  private final ScriptedLedgerPort ledger = new ScriptedLedgerPort();
  private final LedgerPoster poster = new LedgerPoster(ledger);

  @Test
  void passesTheRequestThroughAndReturnsTheLedgersAnswer() {
    PostingOutcome posted = new PostingOutcome.Posted(LEDGER_TS, false);
    PostingOutcome rejected = new PostingOutcome.Rejected(FailureCode.INSUFFICIENT_FUNDS, 1);
    PostingOutcome unknown = new PostingOutcome.Unknown(UnknownReason.LEDGER_TIMEOUT);
    ledger.thenReturn(posted).thenReturn(rejected).thenReturn(unknown);

    assertThat(poster.post(REQUEST)).isEqualTo(posted);
    assertThat(poster.post(REQUEST)).isEqualTo(rejected);
    assertThat(poster.post(REQUEST)).isEqualTo(unknown);
    assertThat(ledger.requests()).containsOnly(REQUEST).hasSize(3);
  }

  @Test
  void saturatedBulkheadIsOverloaded() {
    ledger.thenThrow(new InvocationRejectedException("ledger bulkhead full", new Object()));

    assertThat(poster.post(REQUEST)).isEqualTo(new PostingOutcome.Unknown(UnknownReason.OVERLOADED));
  }

  @Test
  void anyOtherRuntimeExceptionIsALedgerError() {
    ledger.thenThrow(new IllegalStateException("boom")).thenThrow(new LedgerUnavailableException("503"));

    assertThat(poster.post(REQUEST)).isEqualTo(new PostingOutcome.Unknown(UnknownReason.LEDGER_ERROR));
    assertThat(poster.post(REQUEST)).isEqualTo(new PostingOutcome.Unknown(UnknownReason.LEDGER_ERROR));
  }
}
