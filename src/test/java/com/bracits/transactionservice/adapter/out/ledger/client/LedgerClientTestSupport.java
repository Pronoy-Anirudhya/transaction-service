package com.bracits.transactionservice.adapter.out.ledger.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.adapter.out.ledger.health.LedgerHealthMonitor;
import com.bracits.transactionservice.adapter.out.ledger.http.impl.LedgerHttpExecutorImpl;
import com.bracits.transactionservice.adapter.out.ledger.mapper.impl.LedgerDtoMapperImpl;
import com.bracits.transactionservice.config.LedgerClientConfig;
import com.bracits.transactionservice.config.properties.LedgerProperties;
import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.ledger.enums.LedgerAccountCode;
import com.bracits.transactionservice.domain.ledger.enums.LedgerAccountFlag;
import com.bracits.transactionservice.domain.ledger.enums.LegCode;
import com.bracits.transactionservice.domain.ledger.model.LedgerAccount;
import com.bracits.transactionservice.domain.ledger.model.Leg;
import com.bracits.transactionservice.domain.ledger.model.PostingRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Shared harness of the ledger client tests ({@link LedgerPostingClientTest},
 * {@link LedgerQueryClientTest}, {@link LedgerAccountsClientTest}): an in-JVM WireMock ledger, the
 * real ledger client wiring of {@link LedgerClientConfig}, and the contract fixtures. Request and
 * response bodies are the examples of {@code ledger-service/openapi/ledger-api.yaml}, verbatim (as
 * JSON).
 */
abstract class LedgerClientTestSupport {

  static final String POSTINGS = "/internal/v1/postings";
  static final String ACCOUNTS = "/internal/v1/accounts";
  static final String FUNDINGS = "/internal/v1/fundings";
  static final String JSON = "application/json";
  static final String PROBLEM_JSON = "application/problem+json";

  // ---- Ids of the contract examples ---------------------------------------------------------------------------------

  static final UUID POSTING_ID = UUID.fromString("0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00");
  static final UUID SENDER = UUID.fromString("0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11");
  static final UUID RECEIVER = UUID.fromString("0192f0b1-4d5e-7f60-9b1c-2d3e4f5a6b22");
  static final UUID MISSING_ACCOUNT = UUID.fromString("0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a99");
  static final UUID FUNDING_ID = UUID.fromString("0192f5a3-1b4d-7e2f-8a6c-3d9e0f1a2b00");
  static final UUID FEE = UUID.fromString("00000000-0000-0000-0000-0000000000c8");
  static final UUID VAT = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
  static final UUID COMMISSION = UUID.fromString("00000000-0000-0000-0000-0000000000dc");
  static final UUID ISSUANCE = UUID.fromString("00000000-0000-0000-0000-000000000384");
  static final long LEDGER_TS = 1791350858928000000L;

  // ---- Contract example bodies --------------------------------------------------------------------------------------

  static final String SEND_MONEY_REQUEST = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","product":1,"userData64":42,
       "legs":[{"debit":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","credit":"0192f0b1-4d5e-7f60-9b1c-2d3e4f5a6b22",
                "amount":100000,"code":10},
               {"debit":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","credit":"00000000-0000-0000-0000-0000000000c8",
                "amount":348,"code":11},
               {"debit":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","credit":"00000000-0000-0000-0000-0000000000d2",
                "amount":65,"code":12},
               {"debit":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","credit":"00000000-0000-0000-0000-0000000000dc",
                "amount":87,"code":13}]}""";

  static final String POSTED = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","status":"POSTED","replay":false,
       "timestamp":1791350858928000000}""";

  static final String POSTED_REPLAY = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","status":"POSTED","replay":true,
       "timestamp":1791350858928000000}""";

  static final String INSUFFICIENT_FUNDS = """
      {"type":"urn:problem:ledger:INSUFFICIENT_FUNDS","title":"Insufficient funds","status":422,
       "detail":"Leg 1 exceeds the debit account's available balance","instance":"/internal/v1/postings",
       "code":"INSUFFICIENT_FUNDS","postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","postingStatus":"REJECTED",
       "legIndex":1}""";

  static final String ACCOUNT_NOT_FOUND_422 = """
      {"type":"urn:problem:ledger:ACCOUNT_NOT_FOUND","title":"Account not found","status":422,
       "detail":"Leg 1 refers to an account that does not exist","instance":"/internal/v1/postings",
       "code":"ACCOUNT_NOT_FOUND","postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","postingStatus":"REJECTED",
       "legIndex":1}""";

  static final String PREVIOUSLY_REJECTED = """
      {"type":"urn:problem:ledger:PREVIOUSLY_REJECTED","title":"Posting previously rejected","status":422,
       "detail":"Posting 0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00 was rejected by an earlier attempt",
       "instance":"/internal/v1/postings","code":"PREVIOUSLY_REJECTED",
       "postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","postingStatus":"REJECTED","legIndex":1}""";

  static final String POSTING_CONFLICT = """
      {"type":"urn:problem:ledger:POSTING_CONFLICT","title":"Posting conflict","status":409,
       "detail":"Posting 0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00 already exists with different content",
       "instance":"/internal/v1/postings","code":"POSTING_CONFLICT",
       "postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","legIndex":1}""";

  static final String POSTING_LEDGER_ERROR = """
      {"type":"urn:problem:ledger:LEDGER_ERROR","title":"Ledger error","status":500,
       "detail":"Unexpected ledger result for posting 0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00",
       "instance":"/internal/v1/postings","code":"LEDGER_ERROR",
       "postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00"}""";

  static final String INTERNAL_ERROR = """
      {"type":"urn:problem:ledger:INTERNAL_ERROR","title":"Internal error","status":500,
       "detail":"Unexpected error","code":"INTERNAL_ERROR"}""";

  static final String POSTING_LEDGER_TIMEOUT = """
      {"type":"urn:problem:ledger:LEDGER_TIMEOUT","title":"Ledger unavailable","status":503,
       "detail":"The ledger did not answer within the deadline; the outcome is unknown",
       "instance":"/internal/v1/postings","code":"LEDGER_TIMEOUT",
       "postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","postingStatus":"UNKNOWN"}""";

  static final String OVERLOADED = """
      {"type":"urn:problem:ledger:OVERLOADED","title":"Overloaded","status":503,
       "detail":"Too many concurrent postings; retry later","instance":"/internal/v1/postings","code":"OVERLOADED"}""";

  static final String LEDGER_UNAVAILABLE = """
      {"type":"urn:problem:ledger:LEDGER_TIMEOUT","title":"Ledger unavailable","status":503,
       "detail":"The ledger did not answer within the deadline","code":"LEDGER_TIMEOUT"}""";

  static final String LOOKUP_POSTED = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","status":"POSTED","timestamp":1791350858928000000}""";

  static final String LOOKUP_NOT_FOUND = """
      {"postingId":"0192f5a4-7c2e-7b1a-9d3e-5f4a2b1c0d00","status":"NOT_FOUND"}""";

  static final String CUSTOMER_WALLET_REQUEST = """
      {"accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","code":100,
       "flags":["DEBITS_MUST_NOT_EXCEED_CREDITS"],"userData64":42}""";

  static final String ACCOUNT_CREATED = """
      {"accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","status":"CREATED"}""";

  static final String ACCOUNT_EXISTS = """
      {"accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","status":"EXISTS"}""";

  static final String ACCOUNT_CONFLICT = """
      {"type":"urn:problem:ledger:ACCOUNT_CONFLICT","title":"Account conflict","status":409,
       "detail":"Account 0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11 already exists with different fields",
       "instance":"/internal/v1/accounts","code":"ACCOUNT_CONFLICT",
       "accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11"}""";

  static final String BALANCE_CUSTOMER_WALLET = """
      {"accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11","debitsPosted":100500,"creditsPosted":500000,
       "debitsPending":0,"creditsPending":0,"available":399500}""";

  static final String BALANCE_ISSUANCE = """
      {"accountId":"00000000-0000-0000-0000-000000000384","debitsPosted":500000,"creditsPosted":0,
       "debitsPending":0,"creditsPending":0,"available":-500000}""";

  static final String BALANCE_NOT_FOUND = """
      {"type":"urn:problem:ledger:ACCOUNT_NOT_FOUND","title":"Account not found","status":404,
       "detail":"Account 0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a99 does not exist",
       "instance":"/internal/v1/accounts/0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a99/balance","code":"ACCOUNT_NOT_FOUND",
       "accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a99"}""";

  static final String FUND_WALLET_REQUEST = """
      {"fundingId":"0192f5a3-1b4d-7e2f-8a6c-3d9e0f1a2b00","accountId":"0192f0b1-2c3d-7e4f-8a5b-6c7d8e9f0a11",
       "amount":500000}""";

  static final String FUNDING_POSTED = """
      {"postingId":"0192f5a3-1b4d-7e2f-8a6c-3d9e0f1a2b00","status":"POSTED","replay":false,
       "timestamp":1791350857104000000}""";

  /**
   * Spec ratios (read 1.2 s / budget 3 s), scaled down: 2 timed-out attempts fit, a third would
   * not.
   */
  static final Duration READ_TIMEOUT = Duration.ofMillis(600);
  static final Duration TOTAL_BUDGET = Duration.ofMillis(1500);

  static final PostingRequest REQUEST = new PostingRequest(POSTING_ID, Product.SEND_MONEY, 42L,
      List.of(
          new Leg(SENDER, RECEIVER, 100_000L, LegCode.PRINCIPAL),
          new Leg(SENDER, FEE, 348L, LegCode.FEE),
          new Leg(SENDER, VAT, 65L, LegCode.VAT),
          new Leg(SENDER, COMMISSION, 87L, LegCode.COMMISSION)));

  static WireMockServer ledger;

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

  // ---- Helpers ----------------------------------------------------------------------------------------------------

  static void run(ContextConsumer<AssertableApplicationContext> test) {
    runner(ledger.baseUrl(), 8).run(ctx -> {
      assertThat(ctx).hasNotFailed();
      test.accept(ctx);
    });
  }

  /**
   * Real ledger client wiring plus the Boot auto-configurations it relies on (AOP defaults to class
   * proxies).
   */
  static ApplicationContextRunner runner(String baseUrl, int concurrencyLimit) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            AopAutoConfiguration.class, JacksonAutoConfiguration.class,
            RestClientAutoConfiguration.class))
        .withUserConfiguration(TestWiring.class)
        .withPropertyValues(
            "poc.ledger.base-url=" + baseUrl,
            "poc.ledger.connect-timeout=100ms",
            "poc.ledger.read-timeout=" + READ_TIMEOUT.toMillis() + "ms",
            "poc.ledger.total-budget=" + TOTAL_BUDGET.toMillis() + "ms",
            "poc.ledger.max-retries=2",
            "poc.ledger.concurrency-limit=" + concurrencyLimit,
            "poc.ledger.health-interval=2s",
            "poc.ledger.retry.initial-delay=50ms",
            "poc.ledger.retry.multiplier=2",
            "poc.ledger.retry.jitter=20ms",
            "poc.ledger.accounts.fee-income=" + FEE,
            "poc.ledger.accounts.vat-payable=" + VAT,
            "poc.ledger.accounts.commission-payable=" + COMMISSION,
            "poc.ledger.accounts.issuance=" + ISSUANCE);
  }

  static int slowerThanReadTimeout() {
    return (int) READ_TIMEOUT.multipliedBy(3).toMillis();
  }

  static MappingBuilder posting() {
    return post(urlEqualTo(POSTINGS));
  }

  static ResponseDefinitionBuilder json(int status, String body) {
    return aResponse().withStatus(status).withHeader("Content-Type", JSON).withBody(body);
  }

  static ResponseDefinitionBuilder problem(int status, String body) {
    return aResponse().withStatus(status).withHeader("Content-Type", PROBLEM_JSON).withBody(body);
  }

  static ResponseDefinitionBuilder unavailable(String body) {
    return problem(503, body).withHeader("Retry-After", "1");
  }

  static void stubLookup(ResponseDefinitionBuilder response) {
    ledger.stubFor(get(urlPathEqualTo(POSTINGS + "/" + POSTING_ID)).willReturn(response));
  }

  static LedgerAccount wallet() {
    return new LedgerAccount(
        SENDER, LedgerAccountCode.CUSTOMER_WALLET,
        Set.of(LedgerAccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS), 42L);
  }

  static void assertAllBodiesIdentical(String path, int expectedRequests) {
    List<LoggedRequest> requests = ledger.findAll(postRequestedFor(urlEqualTo(path)));
    assertThat(requests).hasSize(expectedRequests);

    byte[] first = requests.getFirst().getBody();
    assertThat(requests).allSatisfy(request -> assertThat(request.getBody()).isEqualTo(first));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(LedgerProperties.class)
  @Import({LedgerClientConfig.class, LedgerHttpExecutorImpl.class, LedgerPostingClient.class,
      LedgerQueryClient.class,
      LedgerAccountsClient.class, LedgerDtoMapperImpl.class, LedgerHealthMonitor.class})
  static class TestWiring {

    @Bean
    SimpleMeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
