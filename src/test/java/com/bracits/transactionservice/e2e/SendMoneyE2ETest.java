package com.bracits.transactionservice.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bracits.transactionservice.domain.event.enums.EventType;
import com.bracits.transactionservice.domain.event.factory.EventIds;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end: real PostgreSQL 18 and RabbitMQ 4 (Testcontainers), the ledger simulated by WireMock
 * speaking the spec 7.2 contract. Skipped automatically when Docker is unavailable.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "TXN_DB_PASSWORD=from-service-connection",
        "RABBIT_USER=from-service-connection",
        "RABBIT_PASSWORD=from-service-connection",
        "API_KEY=" + SendMoneyE2ETest.API_KEY,
        "QUOTE_SIGNING_KEY=e2e-quote-signing-key",
        "poc.ledger.read-timeout=300ms",
        "poc.ledger.total-budget=1s",
        "poc.ledger.health-interval=100ms",
        "poc.repair.interval=250ms",
        "poc.repair.min-age=500ms",
        "poc.repair.unknown-recheck=250ms",
        "poc.repair.initial-backoff=250ms",
        "management.tracing.sampling.probability=0"
    })
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SendMoneyE2ETest {

  static final String API_KEY = "e2e-api-key";
  private static final String AUDIT_QUEUE = "audit.send-money";
  private static final String POSTINGS = "/internal/v1/postings";
  private static final String READINESS = "/actuator/health/readiness";
  private static final long FUNDING = 10_000_000L;
  private static final long INSUFFICIENT_AMOUNT = 777_700L;
  private static final long SLOW_AMOUNT = 555_500L;
  private static final AtomicInteger MSISDN_SEQ = new AtomicInteger(
      ThreadLocalRandom.current().nextInt(1_000_000));

  @Container
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

  @Container
  @ServiceConnection
  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

  static final WireMockServer LEDGER = new WireMockServer(
      options().dynamicPort().globalTemplating(true));

  static {
    LEDGER.start();
    stubLedger();
  }

  private final Map<String, Message> events = new ConcurrentHashMap<>();

  @LocalServerPort
  int port;

  @Autowired
  RabbitTemplate rabbit;

  @Autowired
  JdbcClient jdbc;

  @Autowired
  JsonMapper json;

  private RestClient http;

  @DynamicPropertySource
  static void ledgerUrl(DynamicPropertyRegistry registry) {
    registry.add("poc.ledger.base-url", LEDGER::baseUrl);
  }

  @AfterAll
  static void stopLedger() {
    LEDGER.stop();
  }

  @BeforeEach
  void setUp() {
    http = RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultHeader("X-API-Key", API_KEY)
        .defaultStatusHandler(status -> true, (request, response) -> {
        })
        .build();
  }

  @Test
  void completedWithQuoteAndEventPublished() {
    String sender = registerAndFund("Rahim Uddin");
    String receiver = registerAndFund("Karim Mia");

    ResponseEntity<JsonNode> quote = postJson("/api/v1/send-money/quote", null,
        Map.of("senderMsisdn", sender, "receiverMsisdn", receiver, "amount", 100_000, "currency",
            "BDT"));
    assertThat(quote.getStatusCode().value()).isEqualTo(200);
    assertThat(quote.getBody().get("fee").asLong()).isEqualTo(500);
    assertThat(quote.getBody().get("receiverName").asString()).isEqualTo("K***m M*a");

    ResponseEntity<JsonNode> sent = postJson("/api/v1/send-money", UUID.randomUUID().toString(),
        Map.of("senderMsisdn", sender, "receiverMsisdn", receiver, "amount", 100_000, "currency",
            "BDT",
            "reference", "Rent", "quoteToken", quote.getBody().get("quoteToken").asString()));

    assertThat(sent.getStatusCode().value()).isEqualTo(200);
    JsonNode body = sent.getBody();
    assertThat(body.get("status").asString()).isEqualTo("COMPLETED");
    assertThat(body.get("fee").asLong()).isEqualTo(500);
    assertThat(body.get("vat").asLong()).isEqualTo(65);
    assertThat(body.get("commission").asLong()).isEqualTo(87);
    assertThat(body.get("totalDebit").asLong()).isEqualTo(100_500);
    UUID txnId = UUID.fromString(body.get("txnId").asString());

    Message event = awaitEvent(txnId);
    assertThat(event.getMessageProperties().getMessageId())
        .isEqualTo(EventIds.of(txnId, EventType.SEND_MONEY_COMPLETED).toString());
    assertThat(event.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(
        MessageDeliveryMode.PERSISTENT);
    assertThat((String) event.getMessageProperties().getHeader("event-type")).isEqualTo(
        "SendMoneyCompleted");

    JsonNode payload = json.readTree(new String(event.getBody(), StandardCharsets.UTF_8));
    assertThat(payload.get("feeIncome").asLong()).isEqualTo(348);
    assertThat(payload.get("failureCode").isNull()).isTrue();

    await().atMost(Duration.ofSeconds(10)).until(() -> publishedMark(txnId));

    ResponseEntity<JsonNode> status = http.get().uri("/api/v1/send-money/{id}", txnId).retrieve()
        .toEntity(JsonNode.class);
    assertThat(status.getBody().get("status").asString()).isEqualTo("COMPLETED");
  }

  @Test
  void insufficientFundsFailsAndReleasesTheLimit() {
    String sender = registerAndFund("Sumon Ahmed");
    String receiver = registerAndFund("Nadia Islam");

    ResponseEntity<JsonNode> sent = postJson("/api/v1/send-money", UUID.randomUUID().toString(),
        Map.of("senderMsisdn", sender, "receiverMsisdn", receiver, "amount", INSUFFICIENT_AMOUNT,
            "currency", "BDT"));

    assertThat(sent.getStatusCode().value()).isEqualTo(422);
    assertThat(sent.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(sent.getBody().get("code").asString()).isEqualTo("INSUFFICIENT_FUNDS");
    UUID txnId = UUID.fromString(sent.getBody().get("txnId").asString());

    Map<String, Object> row = jdbc.sql(
            "SELECT status, failure_code FROM send_money_txn WHERE txn_id = ?")
        .param(txnId).query().singleRow();
    assertThat(row).containsEntry("status", "FAILED")
        .containsEntry("failure_code", "INSUFFICIENT_FUNDS");

    Map<String, Object> usage = jdbc.sql("""
            SELECT u.day_amount, u.day_count, u.month_amount, u.month_count
              FROM wallet_limit_usage u JOIN wallet w ON w.wallet_id = u.wallet_id
             WHERE w.msisdn = ?""")
        .param(sender).query().singleRow();
    assertThat(((Number) usage.get("day_amount")).longValue()).isZero();
    assertThat(((Number) usage.get("day_count")).intValue()).isZero();
    assertThat(((Number) usage.get("month_amount")).longValue()).isZero();
    assertThat(((Number) usage.get("month_count")).intValue()).isZero();

    Message event = awaitEvent(txnId);
    assertThat((String) event.getMessageProperties().getHeader("event-type")).isEqualTo(
        "SendMoneyFailed");
  }

  @Test
  void idempotentReplayAndConflict() {
    String sender = registerAndFund("Tania Akter");
    String receiver = registerAndFund("Jamal Hossain");
    String key = UUID.randomUUID().toString();
    Map<String, Object> request =
        Map.of("senderMsisdn", sender, "receiverMsisdn", receiver, "amount", 200_000, "currency",
            "BDT");

    ResponseEntity<JsonNode> first = postJson("/api/v1/send-money", key, request);
    ResponseEntity<JsonNode> replay = postJson("/api/v1/send-money", key, request);

    assertThat(first.getStatusCode().value()).isEqualTo(200);
    assertThat(replay.getStatusCode().value()).isEqualTo(200);
    assertThat(replay.getBody().get("txnId").asString()).isEqualTo(
        first.getBody().get("txnId").asString());
    LEDGER.verify(1, postRequestedFor(urlPathEqualTo(POSTINGS))
        .withRequestBody(
            matchingJsonPath("$.postingId", equalTo(first.getBody().get("txnId").asString()))));

    ResponseEntity<JsonNode> conflict = postJson("/api/v1/send-money", key,
        Map.of("senderMsisdn", sender, "receiverMsisdn", receiver, "amount", 200_001, "currency",
            "BDT"));
    assertThat(conflict.getStatusCode().value()).isEqualTo(409);
    assertThat(conflict.getBody().get("code").asString()).isEqualTo("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void ledgerTimeoutReturns202ThenRepairCompletes() {
    String sender = registerAndFund("Farhana Begum");
    String receiver = registerAndFund("Habib Rahman");
    StubMapping slow = LEDGER.stubFor(post(urlEqualTo(POSTINGS)).atPriority(1)
        .withRequestBody(matchingJsonPath("$.legs[0].amount", equalTo(Long.toString(SLOW_AMOUNT))))
        .willReturn(okJson(
            "{\"postingId\":\"{{jsonPath request.body '$.postingId'}}\",\"status\":\"POSTED\","
                + "\"replay\":false,\"timestamp\":1}").withFixedDelay(2_000)));

    ResponseEntity<JsonNode> sent = postJson("/api/v1/send-money", UUID.randomUUID().toString(),
        Map.of("senderMsisdn", sender, "receiverMsisdn", receiver, "amount", SLOW_AMOUNT,
            "currency", "BDT"));

    assertThat(sent.getStatusCode().value()).isEqualTo(202);
    assertThat(sent.getBody().get("status").asString()).isEqualTo("PROCESSING");
    assertThat(sent.getBody().get("message").asString()).contains("ledger-service");
    UUID txnId = UUID.fromString(sent.getBody().get("txnId").asString());

    LEDGER.removeStub(slow);

    await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200))
        .until(() -> "COMPLETED".equals(
            http.get().uri("/api/v1/send-money/{id}", txnId).retrieve().toEntity(JsonNode.class)
                .getBody().get("status").asString()));

    Message event = awaitEvent(txnId);
    assertThat((String) event.getMessageProperties().getHeader("event-type")).isEqualTo(
        "SendMoneyCompleted");
  }

  @Test
  void ledgerOutageAnswersLedgerUnavailableAndRecovers() {
    String sender = registerAndFund("Mitu Das");
    String receiver = registerAndFund("Rana Sen");
    Map<String, Object> request =
        Map.of("senderMsisdn", sender, "receiverMsisdn", receiver, "amount", 100_000, "currency",
            "BDT");
    StubMapping outage = LEDGER.stubFor(get(urlEqualTo(READINESS)).atPriority(1)
        .willReturn(aResponse().withStatus(503)));
    try {
      await().atMost(Duration.ofSeconds(10)).until(() -> "DOWN".equals(ledgerHealth()));

      ResponseEntity<JsonNode> sent = postJson("/api/v1/send-money", UUID.randomUUID().toString(),
          request);
      ResponseEntity<JsonNode> quote = postJson("/api/v1/send-money/quote", null, request);
      ResponseEntity<JsonNode> balance = http.get().uri("/api/v1/wallets/{msisdn}/balance", sender)
          .retrieve().toEntity(JsonNode.class);

      assertThat(sent.getStatusCode().value()).isEqualTo(503);
      assertThat(sent.getHeaders().getFirst("Retry-After")).isEqualTo("1");
      assertThat(sent.getBody().get("code").asString()).isEqualTo("LEDGER_UNAVAILABLE");
      assertThat(quote.getStatusCode().value()).isEqualTo(200);
      assertThat(balance.getStatusCode().value()).isEqualTo(503);
      assertThat(balance.getBody().get("code").asString()).isEqualTo("LEDGER_UNAVAILABLE");
    } finally {
      LEDGER.removeStub(outage);
      await().atMost(Duration.ofSeconds(10)).until(() -> "UP".equals(ledgerHealth()));
    }

    assertThat(postJson("/api/v1/send-money", UUID.randomUUID().toString(), request)
        .getStatusCode().value()).isEqualTo(200);
  }

  private String ledgerHealth() {
    return RestClient.create("http://localhost:" + port).get().uri("/actuator/health")
        .retrieve().onStatus(status -> true, (req, res) -> {
        }).body(JsonNode.class).get("components").get("ledger").get("status").asString();
  }

  @Test
  void missingApiKeyIsRejected() {
    ResponseEntity<JsonNode> response = RestClient.create("http://localhost:" + port).get()
        .uri("/api/v1/send-money/{id}", UUID.randomUUID())
        .retrieve().onStatus(status -> true, (request, resp) -> {
        }).toEntity(JsonNode.class);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(response.getBody().get("code").asString()).isEqualTo("UNAUTHORIZED");
  }

  private String registerAndFund(String holderName) {
    String msisdn = "0171%07d".formatted(MSISDN_SEQ.incrementAndGet() % 10_000_000);

    ResponseEntity<JsonNode> registered = postJson("/api/v1/wallets", null,
        Map.of("msisdn", msisdn, "holderName", holderName, "kycTier", 1));
    assertThat(registered.getStatusCode().value()).isEqualTo(201);

    ResponseEntity<JsonNode> funded = postJson("/api/v1/wallets/" + msisdn + "/fund",
        UUID.randomUUID().toString(),
        Map.of("amount", FUNDING));
    assertThat(funded.getStatusCode().value()).isEqualTo(200);
    return msisdn;
  }

  private ResponseEntity<JsonNode> postJson(String path, String idempotencyKey,
      Map<String, Object> body) {
    RestClient.RequestBodySpec request = http.post().uri(path)
        .contentType(MediaType.APPLICATION_JSON);
    if (idempotencyKey != null) {
      request.header("Idempotency-Key", idempotencyKey);
    }
    return request.body(body).retrieve().toEntity(JsonNode.class);
  }

  private boolean publishedMark(UUID txnId) {
    return jdbc.sql("SELECT event_published_at IS NOT NULL FROM send_money_txn WHERE txn_id = ?")
        .param(txnId).query(Boolean.class).single();
  }

  /**
   * Drains the demo quorum queue into a map keyed by the {@code txn-id} header until the txn's
   * event shows up.
   */
  private Message awaitEvent(UUID txnId) {
    await().atMost(Duration.ofSeconds(15)).until(() -> {
      Message message;
      while ((message = rabbit.receive(AUDIT_QUEUE, 200)) != null) {
        events.put(String.valueOf(message.getMessageProperties().<Object>getHeader("txn-id")),
            message);
      }

      return events.containsKey(txnId.toString());
    });
    return events.get(txnId.toString());
  }

  /**
   * Registered once: per-test resets would race the app's readiness probe and flip the gate.
   */
  private static void stubLedger() {
    LEDGER.stubFor(
        get(urlEqualTo(READINESS)).atPriority(10).willReturn(okJson("{\"status\":\"UP\"}")));
    LEDGER.stubFor(post(urlEqualTo("/internal/v1/accounts"))
        .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
            .withBody(
                "{\"accountId\":\"{{jsonPath request.body '$.accountId'}}\",\"status\":\"CREATED\"}")));
    LEDGER.stubFor(post(urlEqualTo("/internal/v1/fundings"))
        .willReturn(okJson(
            "{\"postingId\":\"{{jsonPath request.body '$.fundingId'}}\",\"status\":\"POSTED\","
                + "\"replay\":false,\"timestamp\":1791350858928000001}")));
    LEDGER.stubFor(post(urlEqualTo(POSTINGS)).atPriority(10)
        .willReturn(okJson(
            "{\"postingId\":\"{{jsonPath request.body '$.postingId'}}\",\"status\":\"POSTED\","
                + "\"replay\":false,\"timestamp\":1791350858928000000}")));
    LEDGER.stubFor(post(urlEqualTo(POSTINGS)).atPriority(1)
        .withRequestBody(
            matchingJsonPath("$.legs[0].amount", equalTo(Long.toString(INSUFFICIENT_AMOUNT))))
        .willReturn(
            aResponse().withStatus(422).withHeader("Content-Type", "application/problem+json")
                .withBody(
                    "{\"type\":\"urn:problem:ledger:INSUFFICIENT_FUNDS\",\"title\":\"Insufficient funds\","
                        + "\"status\":422,\"code\":\"INSUFFICIENT_FUNDS\","
                        + "\"postingId\":\"{{jsonPath request.body '$.postingId'}}\",\"postingStatus\":\"REJECTED\","
                        + "\"legIndex\":1}")));
  }
}
