package com.bracits.transactionservice.adapter.out.ledger;

import com.bracits.transactionservice.adapter.out.ledger.mapper.LedgerDtoMapper;
import com.bracits.transactionservice.config.LedgerClientConfig;
import com.bracits.transactionservice.config.LedgerProperties;
import com.bracits.transactionservice.config.MetricConstants;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.Product;
import com.bracits.transactionservice.domain.ledger.AccountBalance;
import com.bracits.transactionservice.domain.ledger.AccountCreation;
import com.bracits.transactionservice.domain.ledger.LedgerAccount;
import com.bracits.transactionservice.domain.ledger.LedgerAccountCode;
import com.bracits.transactionservice.domain.ledger.LedgerAccountFlag;
import com.bracits.transactionservice.domain.ledger.Leg;
import com.bracits.transactionservice.domain.ledger.LegCode;
import com.bracits.transactionservice.domain.ledger.PostingLookup;
import com.bracits.transactionservice.domain.ledger.PostingLookupStatus;
import com.bracits.transactionservice.domain.ledger.PostingOutcome;
import com.bracits.transactionservice.domain.ledger.PostingOutcome.Posted;
import com.bracits.transactionservice.domain.ledger.PostingOutcome.Rejected;
import com.bracits.transactionservice.domain.ledger.PostingOutcome.Unknown;
import com.bracits.transactionservice.domain.ledger.PostingRequest;
import com.bracits.transactionservice.domain.ledger.UnknownReason;
import com.bracits.transactionservice.port.out.LedgerAccountNotFoundException;
import com.bracits.transactionservice.port.out.LedgerAccountsPort;
import com.bracits.transactionservice.port.out.LedgerConflictException;
import com.bracits.transactionservice.port.out.LedgerPort;
import com.bracits.transactionservice.port.out.LedgerQueryPort;
import com.bracits.transactionservice.port.out.LedgerUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.resilience.InvocationRejectedException;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * HttpLedgerClient against an in-JVM WireMock ledger, wired by the real {@link LedgerClientConfig}. Request and
 * response bodies are the examples of {@code ledger-service/openapi/ledger-api.yaml}, verbatim (as JSON).
 */
class HttpLedgerClientTest {

  private static final String POSTINGS = "/internal/v1/postings";
  private static final String ACCOUNTS = "/internal/v1/accounts";
  private static final String FUNDINGS = "/internal/v1/fundings";
  private static final String JSON = "application/json";
  private static final String PROBLEM_JSON = "application/problem+json";

  // ---- Ids of the contract examples ---------------------------------------------------------------------------------

  private static final UUID POSTING_ID = UUID.fromString("0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00");
  private static final UUID SENDER = UUID.fromString("0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11");
  private static final UUID RECEIVER = UUID.fromString("0192f0b1-4d5e-7f60-9b1c-2d3e4f5a6b22");
  private static final UUID MISSING_ACCOUNT = UUID.fromString("0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a99");
  private static final UUID FUNDING_ID = UUID.fromString("0192f5a3-1b4d-7e2f-8a6c-3d9e0f1a2b00");
  private static final UUID FEE = UUID.fromString("00000000-0000-0000-0000-0000000000c8");
  private static final UUID VAT = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
  private static final UUID COMMISSION = UUID.fromString("00000000-0000-0000-0000-0000000000dc");
  private static final UUID ISSUANCE = UUID.fromString("00000000-0000-0000-0000-000000000384");
  private static final long LEDGER_TS = 1791350858928000000L;

  // ---- Contract example bodies --------------------------------------------------------------------------------------

  private static final String SEND_MONEY_REQUEST = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","product":1,"userData64":42,
       "legs":[{"debit":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","credit":"0192f0b1-4d5e-7f60-9b1c-2d3e4f5a6b22",
                "amount":100000,"code":10},
               {"debit":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","credit":"00000000-0000-0000-0000-0000000000c8",
                "amount":348,"code":11},
               {"debit":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","credit":"00000000-0000-0000-0000-0000000000d2",
                "amount":65,"code":12},
               {"debit":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","credit":"00000000-0000-0000-0000-0000000000dc",
                "amount":87,"code":13}]}""";

  private static final String POSTED = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","status":"POSTED","replay":false,
       "timestamp":1791350858928000000}""";

  private static final String POSTED_REPLAY = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","status":"POSTED","replay":true,
       "timestamp":1791350858928000000}""";

  private static final String INSUFFICIENT_FUNDS = """
      {"type":"urn:problem:ledger:INSUFFICIENT_FUNDS","title":"Insufficient funds","status":422,
       "detail":"Leg 1 exceeds the debit account's available balance","instance":"/internal/v1/postings",
       "code":"INSUFFICIENT_FUNDS","postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","postingStatus":"REJECTED",
       "legIndex":1}""";

  private static final String ACCOUNT_NOT_FOUND_422 = """
      {"type":"urn:problem:ledger:ACCOUNT_NOT_FOUND","title":"Account not found","status":422,
       "detail":"Leg 1 refers to an account that does not exist","instance":"/internal/v1/postings",
       "code":"ACCOUNT_NOT_FOUND","postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","postingStatus":"REJECTED",
       "legIndex":1}""";

  private static final String PREVIOUSLY_REJECTED = """
      {"type":"urn:problem:ledger:PREVIOUSLY_REJECTED","title":"Posting previously rejected","status":422,
       "detail":"Posting 0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00 was rejected by an earlier attempt",
       "instance":"/internal/v1/postings","code":"PREVIOUSLY_REJECTED",
       "postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","postingStatus":"REJECTED","legIndex":1}""";

  private static final String POSTING_CONFLICT = """
      {"type":"urn:problem:ledger:POSTING_CONFLICT","title":"Posting conflict","status":409,
       "detail":"Posting 0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00 already exists with different content",
       "instance":"/internal/v1/postings","code":"POSTING_CONFLICT",
       "postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","legIndex":1}""";

  private static final String POSTING_LEDGER_ERROR = """
      {"type":"urn:problem:ledger:LEDGER_ERROR","title":"Ledger error","status":500,
       "detail":"Unexpected ledger result for posting 0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00",
       "instance":"/internal/v1/postings","code":"LEDGER_ERROR",
       "postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00"}""";

  private static final String INTERNAL_ERROR = """
      {"type":"urn:problem:ledger:INTERNAL_ERROR","title":"Internal error","status":500,
       "detail":"Unexpected error","code":"INTERNAL_ERROR"}""";

  private static final String POSTING_LEDGER_TIMEOUT = """
      {"type":"urn:problem:ledger:LEDGER_TIMEOUT","title":"Ledger unavailable","status":503,
       "detail":"The ledger did not answer within the deadline; the outcome is unknown",
       "instance":"/internal/v1/postings","code":"LEDGER_TIMEOUT",
       "postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","postingStatus":"UNKNOWN"}""";

  private static final String OVERLOADED = """
      {"type":"urn:problem:ledger:OVERLOADED","title":"Overloaded","status":503,
       "detail":"Too many concurrent postings; retry later","instance":"/internal/v1/postings","code":"OVERLOADED"}""";

  private static final String LEDGER_UNAVAILABLE = """
      {"type":"urn:problem:ledger:LEDGER_TIMEOUT","title":"Ledger unavailable","status":503,
       "detail":"The ledger did not answer within the deadline","code":"LEDGER_TIMEOUT"}""";

  private static final String LOOKUP_POSTED = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","status":"POSTED","timestamp":1791350858928000000}""";

  private static final String LOOKUP_NOT_FOUND = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","status":"NOT_FOUND"}""";

  private static final String CUSTOMER_WALLET_REQUEST = """
      {"accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","code":100,
       "flags":["DEBITS_MUST_NOT_EXCEED_CREDITS"],"userData64":42}""";

  private static final String ACCOUNT_CREATED = """
      {"accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","status":"CREATED"}""";

  private static final String ACCOUNT_EXISTS = """
      {"accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","status":"EXISTS"}""";

  private static final String ACCOUNT_CONFLICT = """
      {"type":"urn:problem:ledger:ACCOUNT_CONFLICT","title":"Account conflict","status":409,
       "detail":"Account 0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11 already exists with different fields",
       "instance":"/internal/v1/accounts","code":"ACCOUNT_CONFLICT",
       "accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11"}""";

  private static final String BALANCE_CUSTOMER_WALLET = """
      {"accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","debitsPosted":100500,"creditsPosted":500000,
       "debitsPending":0,"creditsPending":0,"available":399500}""";

  private static final String BALANCE_ISSUANCE = """
      {"accountId":"00000000-0000-0000-0000-000000000384","debitsPosted":500000,"creditsPosted":0,
       "debitsPending":0,"creditsPending":0,"available":-500000}""";

  private static final String BALANCE_NOT_FOUND = """
      {"type":"urn:problem:ledger:ACCOUNT_NOT_FOUND","title":"Account not found","status":404,
       "detail":"Account 0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a99 does not exist",
       "instance":"/internal/v1/accounts/0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a99/balance","code":"ACCOUNT_NOT_FOUND",
       "accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a99"}""";

  private static final String FUND_WALLET_REQUEST = """
      {"fundingId":"0192f5a3-1b4d-7e2f-8a6c-3d9e0f1a2b00","accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11",
       "amount":500000}""";

  private static final String FUNDING_POSTED = """
      {"postingId":"0192f5a3-1b4d-7e2f-8a6c-3d9e0f1a2b00","status":"POSTED","replay":false,
       "timestamp":1791350857104000000}""";

  /** Spec ratios (read 1.2 s / budget 3 s), scaled down: 2 timed-out attempts fit, a third would not. */
  private static final Duration READ_TIMEOUT = Duration.ofMillis(600);
  private static final Duration TOTAL_BUDGET = Duration.ofMillis(1500);

  private static final PostingRequest REQUEST = new PostingRequest(POSTING_ID, Product.SEND_MONEY, 42L, List.of(
      new Leg(SENDER, RECEIVER, 100_000L, LegCode.PRINCIPAL),
      new Leg(SENDER, FEE, 348L, LegCode.FEE),
      new Leg(SENDER, VAT, 65L, LegCode.VAT),
      new Leg(SENDER, COMMISSION, 87L, LegCode.COMMISSION)));

  private static WireMockServer ledger;

  @BeforeAll
  static void startLedger() {
    ledger = new WireMockServer(options().dynamicPort());
    ledger.start();
  }

  @AfterAll
  static void stopLedger() {
    ledger.stop();
  }

  @BeforeEach
  void reset() {
    ledger.resetAll();
  }

  // ---- Posting: answers --------------------------------------------------------------------------------------------

  @Test
  void postedMapsTimestampAndRecordsTimer() {
    ledger.stubFor(posting().willReturn(json(200, POSTED)));
    run(ctx -> {
      assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(new Posted(LEDGER_TS, false));
      Timer timer = ctx.getBean(MeterRegistry.class).find(MetricConstants.LEDGER_POSTING_DURATION)
          .tag(MetricConstants.TAG_OUTCOME, LedgerApiConstants.OUTCOME_POSTED).timer();
      assertThat(timer).isNotNull();
      assertThat(timer.count()).isEqualTo(1);
    });
  }

  @Test
  void postedReplay() {
    ledger.stubFor(posting().willReturn(json(200, POSTED_REPLAY)));
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(new Posted(LEDGER_TS, true)));
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
          .tag(MetricConstants.TAG_OUTCOME, LedgerApiConstants.OUTCOME_REJECTED).timer()).isNotNull();
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
      assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(new Unknown(UnknownReason.LEDGER_TIMEOUT));
      // Retry-After: 1 is not slept inside the request budget; spec 8.3 backoff (50 ms, 100 ms + jitter) applies.
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
      assertThat(ctx.getBean(MeterRegistry.class).find(MetricConstants.LEDGER_POSTING_DURATION)
          .tag(MetricConstants.TAG_OUTCOME, LedgerApiConstants.OUTCOME_TIMEOUT).timer()).isNotNull();
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
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(new Posted(LEDGER_TS, true)));
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
    run(ctx -> assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(new Posted(LEDGER_TS, true)));
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
      assertThat(elapsed).isGreaterThanOrEqualTo(READ_TIMEOUT.multipliedBy(2)).isLessThan(TOTAL_BUDGET);
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
      assertThat(ctx.getBean(LedgerPort.class).post(REQUEST)).isEqualTo(new Unknown(UnknownReason.LEDGER_TIMEOUT));
    });
  }

  // ---- Posting: bulkhead ------------------------------------------------------------------------------------------

  @Test
  void bulkheadRejectsWhenSaturated() {
    ledger.stubFor(posting().willReturn(json(200, POSTED).withFixedDelay((int) READ_TIMEOUT.toMillis() / 2)));
    runner(ledger.baseUrl(), 1).run(ctx -> {
      assertThat(ctx).hasNotFailed();
      LedgerPort port = ctx.getBean(LedgerPort.class);
      assertThat(AopUtils.isCglibProxy(port)).isTrue();
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        CompletableFuture<PostingOutcome> first = CompletableFuture.supplyAsync(() -> port.post(REQUEST), executor);
        await().atMost(Duration.ofSeconds(2))
            .until(() -> ledger.findAll(postRequestedFor(urlEqualTo(POSTINGS))).size() == 1);
        assertThatThrownBy(() -> port.post(REQUEST)).isInstanceOf(InvocationRejectedException.class);
        assertThat(first.join()).isEqualTo(new Posted(LEDGER_TS, false));
      }
      // The permit is released: the next call goes through.
      assertThat(port.post(REQUEST)).isEqualTo(new Posted(LEDGER_TS, false));
    });
    ledger.verify(exactly(2), postRequestedFor(urlEqualTo(POSTINGS)));
  }

  // ---- Lookup and balance -----------------------------------------------------------------------------------------

  @Test
  void lookupPostedCarriesTimestamp() {
    stubLookup(json(200, LOOKUP_POSTED));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isEqualTo(new PostingLookup(PostingLookupStatus.POSTED, OptionalLong.of(LEDGER_TS))));
    ledger.verify(exactly(1), getRequestedFor(urlPathEqualTo(POSTINGS + "/" + POSTING_ID))
        .withQueryParam("legs", equalTo("4")));
  }

  @Test
  void lookupPostedWithoutTimestampIsEmpty() {
    stubLookup(json(200, "{\"postingId\":\"" + POSTING_ID + "\",\"status\":\"POSTED\"}"));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isEqualTo(new PostingLookup(PostingLookupStatus.POSTED, OptionalLong.empty())));
  }

  @Test
  void lookupNotFound() {
    stubLookup(json(200, LOOKUP_NOT_FOUND));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isEqualTo(new PostingLookup(PostingLookupStatus.NOT_FOUND, OptionalLong.empty())));
  }

  @Test
  void lookupRejectsLegCountOutsideContractRange() {
    run(ctx -> {
      LedgerQueryPort port = ctx.getBean(LedgerQueryPort.class);
      assertThatThrownBy(() -> port.lookupPosting(POSTING_ID, 0)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> port.lookupPosting(POSTING_ID, 9)).isInstanceOf(IllegalArgumentException.class);
    });
    assertThat(ledger.getAllServeEvents()).isEmpty();
  }

  @Test
  void lookupUnavailableAfterRetries() {
    stubLookup(unavailable(LEDGER_UNAVAILABLE));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isInstanceOf(LedgerUnavailableException.class));
    ledger.verify(exactly(3), getRequestedFor(urlPathEqualTo(POSTINGS + "/" + POSTING_ID)));
  }

  @Test
  void lookupServerErrorIsUnavailableWithoutRetry() {
    stubLookup(problem(500, INTERNAL_ERROR));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerQueryPort.class).lookupPosting(POSTING_ID, 4))
        .isInstanceOf(LedgerUnavailableException.class));
    ledger.verify(exactly(1), getRequestedFor(urlPathEqualTo(POSTINGS + "/" + POSTING_ID)));
  }

  @Test
  void balanceOfCustomerWallet() {
    ledger.stubFor(get(urlEqualTo(ACCOUNTS + "/" + SENDER + "/balance")).willReturn(json(200, BALANCE_CUSTOMER_WALLET)));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).balance(SENDER))
        .isEqualTo(new AccountBalance(100_500, 500_000, 0, 0, 399_500)));
  }

  @Test
  void balanceOfIssuanceIsNegative() {
    ledger.stubFor(get(urlEqualTo(ACCOUNTS + "/" + ISSUANCE + "/balance")).willReturn(json(200, BALANCE_ISSUANCE)));
    run(ctx -> assertThat(ctx.getBean(LedgerQueryPort.class).balance(ISSUANCE))
        .isEqualTo(new AccountBalance(500_000, 0, 0, 0, -500_000)));
  }

  @Test
  void balanceNotFound() {
    ledger.stubFor(get(urlEqualTo(ACCOUNTS + "/" + MISSING_ACCOUNT + "/balance"))
        .willReturn(problem(404, BALANCE_NOT_FOUND)));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerQueryPort.class).balance(MISSING_ACCOUNT))
        .isInstanceOf(LedgerAccountNotFoundException.class));
  }

  // ---- Accounts and funding ---------------------------------------------------------------------------------------

  @Test
  void createAccountCreatedMatchesContractExample() {
    ledger.stubFor(post(urlEqualTo(ACCOUNTS)).willReturn(json(201, ACCOUNT_CREATED)));
    run(ctx -> assertThat(ctx.getBean(LedgerAccountsPort.class).createAccount(wallet()))
        .isEqualTo(AccountCreation.CREATED));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(ACCOUNTS))
        .withHeader("Content-Type", containing(JSON))
        .withRequestBody(equalToJson(CUSTOMER_WALLET_REQUEST, false, false)));
  }

  @Test
  void createAccountAlreadyExists() {
    ledger.stubFor(post(urlEqualTo(ACCOUNTS)).willReturn(json(200, ACCOUNT_EXISTS)));
    run(ctx -> assertThat(ctx.getBean(LedgerAccountsPort.class).createAccount(wallet()))
        .isEqualTo(AccountCreation.ALREADY_EXISTS));
  }

  @Test
  void createAccountConflict() {
    ledger.stubFor(post(urlEqualTo(ACCOUNTS)).willReturn(problem(409, ACCOUNT_CONFLICT)));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerAccountsPort.class).createAccount(wallet()))
        .isInstanceOf(LedgerConflictException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(ACCOUNTS)));
  }

  @Test
  void fundPostedMatchesContractExample() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(json(200, FUNDING_POSTED)));
    run(ctx -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 500_000L));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS))
        .withHeader("Content-Type", containing(JSON))
        .withRequestBody(equalToJson(FUND_WALLET_REQUEST, false, false)));
  }

  @Test
  void fundAccountNotFound() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(problem(422, ACCOUNT_NOT_FOUND_422)));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, MISSING_ACCOUNT, 1L))
        .isInstanceOf(LedgerAccountNotFoundException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS)));
  }

  @Test
  void fundPreviouslyRejectedIsUnavailable() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(problem(422, PREVIOUSLY_REJECTED)));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 1L))
        .isInstanceOf(LedgerUnavailableException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS)));
  }

  @Test
  void fundConflict() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(problem(409, POSTING_CONFLICT)));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 1L))
        .isInstanceOf(LedgerConflictException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS)));
  }

  @Test
  void fundServerErrorIsUnavailableWithoutRetry() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(problem(500, POSTING_LEDGER_ERROR)));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 1L))
        .isInstanceOf(LedgerUnavailableException.class));
    ledger.verify(exactly(1), postRequestedFor(urlEqualTo(FUNDINGS)));
  }

  @Test
  void fundUnavailableAfterIdenticalRetries() {
    ledger.stubFor(post(urlEqualTo(FUNDINGS)).willReturn(unavailable(OVERLOADED)));
    run(ctx -> assertThatThrownBy(() -> ctx.getBean(LedgerAccountsPort.class).fund(FUNDING_ID, SENDER, 1L))
        .isInstanceOf(LedgerUnavailableException.class));
    ledger.verify(exactly(3), postRequestedFor(urlEqualTo(FUNDINGS)));
    assertAllBodiesIdentical(FUNDINGS, 3);
  }

  // ---- Helpers ----------------------------------------------------------------------------------------------------

  private static void run(ContextConsumer<AssertableApplicationContext> test) {
    runner(ledger.baseUrl(), 8).run(ctx -> {
      assertThat(ctx).hasNotFailed();
      test.accept(ctx);
    });
  }

  /** Real ledger client wiring plus the Boot auto-configurations it relies on (AOP defaults to class proxies). */
  private static ApplicationContextRunner runner(String baseUrl, int concurrencyLimit) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            AopAutoConfiguration.class, JacksonAutoConfiguration.class, RestClientAutoConfiguration.class))
        .withUserConfiguration(TestWiring.class)
        .withPropertyValues(
            "poc.ledger.base-url=" + baseUrl,
            "poc.ledger.connect-timeout=100ms",
            "poc.ledger.read-timeout=" + READ_TIMEOUT.toMillis() + "ms",
            "poc.ledger.total-budget=" + TOTAL_BUDGET.toMillis() + "ms",
            "poc.ledger.max-retries=2",
            "poc.ledger.concurrency-limit=" + concurrencyLimit,
            "poc.ledger.retry.initial-delay=50ms",
            "poc.ledger.retry.multiplier=2",
            "poc.ledger.retry.jitter=20ms",
            "poc.ledger.accounts.fee-income=" + FEE,
            "poc.ledger.accounts.vat-payable=" + VAT,
            "poc.ledger.accounts.commission-payable=" + COMMISSION,
            "poc.ledger.accounts.issuance=" + ISSUANCE);
  }

  private static int slowerThanReadTimeout() {
    return (int) READ_TIMEOUT.multipliedBy(3).toMillis();
  }

  private static MappingBuilder posting() {
    return post(urlEqualTo(POSTINGS));
  }

  private static ResponseDefinitionBuilder json(int status, String body) {
    return aResponse().withStatus(status).withHeader("Content-Type", JSON).withBody(body);
  }

  private static ResponseDefinitionBuilder problem(int status, String body) {
    return aResponse().withStatus(status).withHeader("Content-Type", PROBLEM_JSON).withBody(body);
  }

  private static ResponseDefinitionBuilder unavailable(String body) {
    return problem(503, body).withHeader("Retry-After", "1");
  }

  private static void stubLookup(ResponseDefinitionBuilder response) {
    ledger.stubFor(get(urlPathEqualTo(POSTINGS + "/" + POSTING_ID)).willReturn(response));
  }

  private static LedgerAccount wallet() {
    return new LedgerAccount(
        SENDER, LedgerAccountCode.CUSTOMER_WALLET, Set.of(LedgerAccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS), 42L);
  }

  private static void assertAllBodiesIdentical(String path, int expectedRequests) {
    List<LoggedRequest> requests = ledger.findAll(postRequestedFor(urlEqualTo(path)));
    assertThat(requests).hasSize(expectedRequests);
    byte[] first = requests.getFirst().getBody();
    assertThat(requests).allSatisfy(request -> assertThat(request.getBody()).isEqualTo(first));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(LedgerProperties.class)
  @Import({LedgerClientConfig.class, HttpLedgerClient.class, LedgerDtoMapper.class})
  static class TestWiring {

    @Bean
    SimpleMeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
