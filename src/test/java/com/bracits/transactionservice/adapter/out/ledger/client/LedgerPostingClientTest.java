package com.bracits.transactionservice.adapter.out.ledger.client;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;
import com.bracits.transactionservice.config.constant.MetricConstants;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.ledger.enums.UnknownReason;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome.Posted;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome.Rejected;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome.Unknown;
import com.bracits.transactionservice.domain.ledger.model.PostingOutcome;
import com.bracits.transactionservice.port.out.client.LedgerPort;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.resilience.InvocationRejectedException;

/**
 * {@link LedgerPostingClient} ({@link LedgerPort}) against an in-JVM WireMock ledger, wired by the
 * real {@code LedgerClientConfig}: posting answers, retries within the budget, the bulkhead and the
 * posting timer. Fixtures and wiring live in {@link LedgerClientTestSupport}.
 */
class LedgerPostingClientTest extends LedgerClientTestSupport {

  // ---- Posting: answers --------------------------------------------------------------------------------------------

  @Test
  void postedMapsTimestampAndRecordsTimer() {
    ledger.stubFor(posting().willReturn(json(200, POSTED)));

    run(ctx -> {
      assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(
          new Posted(LEDGER_TS, false));

      Timer timer = ctx.getBean(MeterRegistry.class).find(MetricConstants.LEDGER_POSTING_DURATION)
          .tag(MetricConstants.TAG_OUTCOME, LedgerApiConstants.OUTCOME_POSTED).timer();
      assertThat(timer).isNotNull();
      assertThat(timer.count()).isEqualTo(1);
    });
  }

  @Test
  void postedReplay() {
    ledger.stubFor(posting().willReturn(json(200, POSTED_REPLAY)));
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(
        new Posted(LEDGER_TS, true)));
  }

  @Test
  void postingRequestMatchesContractExample() {
    ledger.stubFor(posting().willReturn(json(200, POSTED)));
    run(ctx -> ctx.getBean(LedgerPort.class).post(REQUEST));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(POSTINGS))
        .withHeader("Content-Type", containing(JSON))
        .withRequestBody(equalToJson(SEND_MONEY_REQUEST, false, false)));
  }

  @Test
  void rejectedInsufficientFundsWithLegIndex() {
    ledger.stubFor(posting().willReturn(problem(422, INSUFFICIENT_FUNDS)));
    run(ctx -> {
      assertThat(ctx.getBean(LedgerPort.class).post(REQUEST))
          .isEqualTo(new Rejected(FailureCode.INSUFFICIENT_FUNDS, 1));
      assertThat(ctx.getBean(MeterRegistry.class).find(MetricConstants.LEDGER_POSTING_DURATION)
          .tag(MetricConstants.TAG_OUTCOME, LedgerApiConstants.OUTCOME_REJECTED)
          .timer()).isNotNull();
    });
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(POSTINGS)));
  }

  @Test
  void rejectedAccountNotFoundIsWalletNotFound() {
    ledger.stubFor(posting().willReturn(problem(422, ACCOUNT_NOT_FOUND_422)));
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST))
        .isEqualTo(new Rejected(FailureCode.WALLET_NOT_FOUND, 1)));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(POSTINGS)));
  }

  @Test
  void rejectedPreviouslyRejectedIsLedgerRejected() {
    ledger.stubFor(posting().willReturn(problem(422, PREVIOUSLY_REJECTED)));
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST))
        .isEqualTo(new Rejected(FailureCode.LEDGER_REJECTED, 1)));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(POSTINGS)));
  }

  @Test
  void rejectedUnknownCodeIsLedgerRejected() {
    ledger.stubFor(posting().willReturn(problem(422,
        "{\"status\":422,\"code\":\"SOMETHING_NEW\",\"postingStatus\":\"REJECTED\",\"legIndex\":3}")));
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST))
        .isEqualTo(new Rejected(FailureCode.LEDGER_REJECTED, 3)));
  }

  @Test
  void conflictIsUnknownWithoutRetry() {
    ledger.stubFor(posting().willReturn(problem(409, POSTING_CONFLICT)));
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST))
        .isEqualTo(new Unknown(UnknownReason.POSTING_CONFLICT)));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(POSTINGS)));
  }

  @ParameterizedTest
  @ValueSource(strings = {POSTING_LEDGER_ERROR, INTERNAL_ERROR})
  void serverErrorIsUnknownWithoutRetry(String body) {
    ledger.stubFor(posting().willReturn(problem(500, body)));
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST))
        .isEqualTo(new Unknown(UnknownReason.LEDGER_ERROR)));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(POSTINGS)));
  }

  @Test
  void unparsableOkIsLedgerError() {
    ledger.stubFor(posting().willReturn(json(200, "not json")));
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST))
        .isEqualTo(new Unknown(UnknownReason.LEDGER_ERROR)));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(POSTINGS)));
  }

  // ---- Posting: retries -------------------------------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {POSTING_LEDGER_TIMEOUT, OVERLOADED})
  void unavailableThreeTimesIsTimeoutAfterExactlyThreeIdenticalRequests(String body) {
    ledger.stubFor(posting().willReturn(unavailable(body)));

    run(ctx -> {
      long start = System.nanoTime();
      assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(
          new Unknown(UnknownReason.LEDGER_TIMEOUT));
      // Retry-After: 1 is not slept inside the request budget; spec 8.3 backoff (50 ms, 100 ms + jitter) applies.
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
      assertThat(ctx.getBean(MeterRegistry.class).find(MetricConstants.LEDGER_POSTING_DURATION)
          .tag(MetricConstants.TAG_OUTCOME, LedgerApiConstants.OUTCOME_TIMEOUT)
          .timer()).isNotNull();
    });

    ledger.verify(exactly(3), postRequestedFor(urlEqualTo(POSTINGS)));
    assertAllBodiesIdentical(POSTINGS, 3);
  }

  @Test
  void overloadedThenPostedReplaySucceedsOnRetry() {
    ledger.stubFor(posting().inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
        .willReturn(unavailable(OVERLOADED))
        .willSetStateTo("recovered"));
    ledger.stubFor(posting().inScenario("retry").whenScenarioStateIs("recovered")
        .willReturn(json(200, POSTED_REPLAY)));

    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(
        new Posted(LEDGER_TS, true)));

    ledger.verify(exactly(2), postRequestedFor(urlEqualTo(POSTINGS)));
    assertAllBodiesIdentical(POSTINGS, 2);
  }

  @Test
  void timeoutThenRejectedIsDefinitive() {
    ledger.stubFor(posting().inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
        .willReturn(unavailable(POSTING_LEDGER_TIMEOUT))
        .willSetStateTo("recovered"));
    ledger.stubFor(posting().inScenario("retry").whenScenarioStateIs("recovered")
        .willReturn(problem(422, INSUFFICIENT_FUNDS)));

    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST))
        .isEqualTo(new Rejected(FailureCode.INSUFFICIENT_FUNDS, 1)));

    ledger.verify(exactly(2), postRequestedFor(urlEqualTo(POSTINGS)));
  }

  @Test
  void readTimeoutIsRetriedThenPosted() {
    ledger.stubFor(posting().inScenario("slow").whenScenarioStateIs(Scenario.STARTED)
        .willReturn(json(200, POSTED).withFixedDelay(slowerThanReadTimeout()))
        .willSetStateTo("fast"));
    ledger.stubFor(posting().inScenario("slow").whenScenarioStateIs("fast")
        .willReturn(json(200, POSTED_REPLAY)));

    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(
        new Posted(LEDGER_TS, true)));

    ledger.verify(exactly(2), postRequestedFor(urlEqualTo(POSTINGS)));
    assertAllBodiesIdentical(POSTINGS, 2);
  }

  @Test
  void twoReadTimeoutsStopWithinTheBudget() {
    ledger.stubFor(posting().willReturn(json(200, POSTED).withFixedDelay(slowerThanReadTimeout())));

    run(ctx -> {
      long start = System.nanoTime();
      PostingOutcome outcome = ctx.getBean(LedgerPort.class).post(REQUEST);
      Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

      assertThat(outcome).isEqualTo(new Unknown(UnknownReason.LEDGER_TIMEOUT));
      assertThat(elapsed).isGreaterThanOrEqualTo(READ_TIMEOUT.multipliedBy(2))
          .isLessThan(TOTAL_BUDGET);
    });

    // B7: after two timed-out attempts less than one read timeout of budget is left, so no third attempt.
    ledger.verify(exactly(2), postRequestedFor(urlEqualTo(POSTINGS)));
    assertAllBodiesIdentical(POSTINGS, 2);
  }

  @Test
  void connectionRefusedIsTimeout() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }

    runner("http://localhost:" + closedPort, 8).run(ctx -> {
      assertThat(ctx).hasNotFailed();
      assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(
          new Unknown(UnknownReason.LEDGER_TIMEOUT));
    });
  }

  // ---- Posting: bulkhead ------------------------------------------------------------------------------------------

  @Test
  void bulkheadRejectsWhenSaturated() {
    ledger.stubFor(
        posting().willReturn(json(200, POSTED).withFixedDelay((int) READ_TIMEOUT.toMillis() / 2)));

    runner(ledger.baseUrl(), 1).run(ctx -> {
      assertThat(ctx).hasNotFailed();

      LedgerPort port = ctx.getBean(LedgerPort.class);
      assertThat(AopUtils.isCglibProxy(port)).isTrue();

      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        CompletableFuture<PostingOutcome> first = CompletableFuture.supplyAsync(
            () -> port.post(REQUEST), executor);
        await().atMost(Duration.ofSeconds(2))
            .until(() -> ledger.findAll(postRequestedFor(urlEqualTo(POSTINGS))).size() == 1);

        assertThatThrownBy(() -> port.post(REQUEST)).isInstanceOf(
            InvocationRejectedException.class);
        assertThat(first.join()).isEqualTo(new Posted(LEDGER_TS, false));
      }

      // The permit is released: the next call goes through.
      assertThat(port.post(REQUEST)).isEqualTo(new Posted(LEDGER_TS, false));
    });

    ledger.verify(exactly(2), postRequestedFor(urlEqualTo(POSTINGS)));
  }
}
