package com.bracits.transactionservice.adapter.out.amqp;

import com.bracits.transactionservice.adapter.out.amqp.mapper.EventMessageMapper;
import com.bracits.transactionservice.config.AmqpConfig;
import com.bracits.transactionservice.config.EventsProperties;
import com.bracits.transactionservice.config.MetricConstants;
import com.bracits.transactionservice.domain.event.SendMoneyEvent;
import com.bracits.transactionservice.port.out.TxnRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The event adapter against a real RabbitMQ 4 (quorum queues): topology, message format, confirms, returns and the
 * batched publish mark. Only the AMQP slice is started; the repository is a recording fake.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = RabbitEventPublisherIntegrationTest.AmqpSlice.class,
    properties = {
        "poc.events.exchange=mfs.transactions",
        "poc.events.confirm-timeout=5s",
        "poc.events.flush-interval=100ms",
        "poc.events.flush-batch-size=500",
        "poc.events.republish.interval=5s",
        "poc.events.republish.batch-size=500",
        "poc.events.republish.min-age=10s",
        "poc.events.republish.lease=30s",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.publisher-returns=true",
        "spring.rabbitmq.template.mandatory=true"
    })
class RabbitEventPublisherIntegrationTest {

  private static final Duration WAIT = Duration.ofSeconds(10);
  private static final String EXCHANGE = "mfs.transactions";

  @Container
  @ServiceConnection
  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

  /** txnIds written by the flusher through {@link TxnRepository#markEventsPublished}. */
  static final Set<UUID> MARKED = ConcurrentHashMap.newKeySet();

  @Autowired
  private RabbitEventPublisher publisher;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private AmqpAdmin amqpAdmin;

  @Autowired
  private MeterRegistry meterRegistry;

  @BeforeEach
  void emptyTheDemoQueue() {
    await().atMost(WAIT).until(() -> amqpAdmin.getQueueInfo(AmqpConstants.AUDIT_QUEUE) != null);
    amqpAdmin.purgeQueue(AmqpConstants.AUDIT_QUEUE, false);
  }

  @Test
  void topologyIsDeclared() throws Exception {
    await().atMost(WAIT).until(() -> amqpAdmin.getQueueInfo(AmqpConstants.AUDIT_QUEUE) != null);

    assertThat(rabbitctl("list_exchanges", "name", "type", "durable"))
        .anySatisfy(row -> assertThat(row).isEqualTo(List.of("mfs.transactions", "topic", "true")))
        .anySatisfy(row -> assertThat(row).isEqualTo(List.of("mfs.transactions.dlx", "topic", "true")));
    assertThat(rabbitctl("list_queues", "name", "type", "durable", "arguments"))
        .anySatisfy(row -> {
          assertThat(row.subList(0, 3)).containsExactly("audit.send-money", "quorum", "true");
          assertThat(row.get(3)).contains("x-dead-letter-exchange").contains("mfs.transactions.dlx")
              .contains("x-queue-type").contains("quorum");
        });
    assertThat(rabbitctl("list_bindings", "source_name", "destination_name", "routing_key"))
        .anySatisfy(row -> assertThat(row).isEqualTo(List.of("mfs.transactions", "audit.send-money",
            "send-money.#")));
  }

  @Test
  void completedEventArrivesPersistentWithDeterministicIdHeadersAndExactPayloadAndIsMarked() {
    SendMoneyEvent event = EventFixtures.completedEvent(EventFixtures.newTxnId());
    double acksBefore = count(MetricConstants.RESULT_ACK);

    publisher.publish(event);

    Message message = rabbitTemplate.receive(AmqpConstants.AUDIT_QUEUE, WAIT.toMillis());
    assertThat(message).isNotNull();
    MessageProperties properties = message.getMessageProperties();
    assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(properties.getMessageId()).isEqualTo(event.eventId().toString());
    assertThat(properties.getContentType()).isEqualTo("application/json");
    assertThat(properties.getReceivedRoutingKey()).isEqualTo("send-money.completed");
    assertThat(properties.getReceivedExchange()).isEqualTo(EXCHANGE);
    assertThat(properties.getHeaders())
        .containsEntry("event-type", "SendMoneyCompleted")
        .containsEntry("schema-version", 1)
        .containsEntry("txn-id", event.txnId().toString())
        .containsEntry("occurred-at", "2026-10-07T09:14:03.211Z");
    String body = new String(message.getBody(), StandardCharsets.UTF_8);
    assertThat(body).isEqualTo(EventFixtures.expectedJson(event)).contains("\"failureCode\":null");

    await().atMost(WAIT).untilAsserted(() -> assertThat(MARKED).contains(event.txnId()));
    assertThat(count(MetricConstants.RESULT_ACK)).isEqualTo(acksBefore + 1);
  }

  @Test
  void failedEventIsRoutedWithItsFailureCode() {
    SendMoneyEvent event = EventFixtures.failedEvent(EventFixtures.newTxnId());

    publisher.publish(event);

    Message message = rabbitTemplate.receive(AmqpConstants.AUDIT_QUEUE, WAIT.toMillis());
    assertThat(message).isNotNull();
    assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("send-money.failed");
    assertThat(message.getMessageProperties().getHeaders()).containsEntry("event-type", "SendMoneyFailed");
    assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
        .isEqualTo(EventFixtures.expectedJson(event))
        .contains("\"ledgerTimestamp\":null,\"failureCode\":\"INSUFFICIENT_FUNDS\"");
    await().atMost(WAIT).untilAsserted(() -> assertThat(MARKED).contains(event.txnId()));
  }

  @Test
  void republishingTheSameEventCarriesTheSameMessageId() {
    SendMoneyEvent event = EventFixtures.completedEvent(EventFixtures.newTxnId());

    publisher.publish(event);
    publisher.publish(event);

    Message first = rabbitTemplate.receive(AmqpConstants.AUDIT_QUEUE, WAIT.toMillis());
    Message second = rabbitTemplate.receive(AmqpConstants.AUDIT_QUEUE, WAIT.toMillis());
    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(second.getMessageProperties().getMessageId())
        .isEqualTo(first.getMessageProperties().getMessageId())
        .isEqualTo(event.eventId().toString());
  }

  @Test
  void unroutableEventIsReturnedAndNotMarked() throws InterruptedException {
    Binding binding = new Binding(AmqpConstants.AUDIT_QUEUE, Binding.DestinationType.QUEUE, EXCHANGE,
        AmqpConstants.SEND_MONEY_BINDING_PATTERN, null);
    SendMoneyEvent event = EventFixtures.completedEvent(EventFixtures.newTxnId());
    double returnedBefore = count(MetricConstants.RESULT_RETURNED);
    double acksBefore = count(MetricConstants.RESULT_ACK);
    amqpAdmin.removeBinding(binding);
    try {
      publisher.publish(event);

      await().atMost(WAIT).until(() -> count(MetricConstants.RESULT_RETURNED) == returnedBefore + 1);
      TimeUnit.MILLISECONDS.sleep(500); // several flush intervals
      assertThat(MARKED).doesNotContain(event.txnId());
      assertThat(count(MetricConstants.RESULT_ACK)).isEqualTo(acksBefore);
    } finally {
      amqpAdmin.declareBinding(binding);
    }
  }

  private double count(String result) {
    return meterRegistry.counter(MetricConstants.EVENTS_PUBLISH, MetricConstants.TAG_RESULT, result).count();
  }

  /** {@code rabbitmqctl list_*} rows as tab-separated columns, without headers. */
  private static List<List<String>> rabbitctl(String command, String... columns) throws Exception {
    String[] args = new String[columns.length + 4];
    args[0] = "rabbitmqctl";
    args[1] = "-q";
    args[2] = "--no-table-headers";
    args[3] = command;
    System.arraycopy(columns, 0, args, 4, columns.length);
    ExecResult result = RABBIT.execInContainer(args);
    assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    return result.getStdout().lines().filter(line -> !line.isBlank()).map(line -> List.of(line.split("\t"))).toList();
  }

  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration({RabbitAutoConfiguration.class, JacksonAutoConfiguration.class})
  @EnableConfigurationProperties(EventsProperties.class)
  @EnableScheduling
  @Import({AmqpConfig.class, RabbitTopologyInitializer.class, RabbitEventPublisher.class, EventMessageMapper.class,
      PublishedMarkBuffer.class, PublishedMarkFlusher.class})
  static class AmqpSlice {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    TxnRepository txnRepository() {
      TxnRepository repository = mock(TxnRepository.class);
      when(repository.markEventsPublished(any())).thenAnswer(invocation -> {
        Collection<UUID> ids = invocation.getArgument(0);
        MARKED.addAll(ids);
        return ids.size();
      });
      return repository;
    }
  }
}
