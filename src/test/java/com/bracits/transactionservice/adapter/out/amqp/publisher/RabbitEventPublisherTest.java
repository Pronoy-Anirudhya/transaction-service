package com.bracits.transactionservice.adapter.out.amqp.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bracits.transactionservice.adapter.out.amqp.constant.AmqpConstants;
import com.bracits.transactionservice.adapter.out.amqp.executor.EventSendExecutor;
import com.bracits.transactionservice.adapter.out.amqp.executor.impl.EventSendExecutorImpl;
import com.bracits.transactionservice.adapter.out.amqp.fixture.EventFixtures;
import com.bracits.transactionservice.adapter.out.amqp.mapper.impl.EventMessageMapperImpl;
import com.bracits.transactionservice.adapter.out.amqp.publishmark.PublishedMarkBuffer;
import com.bracits.transactionservice.adapter.out.amqp.publishmark.PublishedMarkFlusher;
import com.bracits.transactionservice.adapter.out.amqp.publishmark.impl.PublishedMarkBufferImpl;
import com.bracits.transactionservice.adapter.out.amqp.publishmark.impl.PublishedMarkFlusherImpl;
import com.bracits.transactionservice.config.constant.MetricConstants;
import com.bracits.transactionservice.config.properties.EventsProperties;
import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ConnectException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import tools.jackson.databind.json.JsonMapper;

class RabbitEventPublisherTest {

  private static final Duration CONFIRM_TIMEOUT = Duration.ofMillis(200);

  private final EventsProperties properties = EventFixtures.properties(CONFIRM_TIMEOUT, 500);
  private final RabbitOperations rabbit = mock(RabbitOperations.class);
  private final PublishedMarkBuffer buffer = new PublishedMarkBufferImpl();
  private final PublishedMarkFlusher flusher =
      new PublishedMarkFlusherImpl(buffer, mock(TxnRepository.class), properties);
  private final EventSendExecutor executor = new EventSendExecutorImpl(
      AmqpConstants.EVENT_SEND_THREAD_PREFIX);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RabbitEventPublisher publisher;

  RabbitEventPublisherTest() {
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    when(connectionFactory.isPublisherConfirms()).thenReturn(true);
    when(connectionFactory.isPublisherReturns()).thenReturn(true);
    when(rabbit.getConnectionFactory()).thenReturn(connectionFactory);

    publisher = new RabbitEventPublisher(rabbit,
        new EventMessageMapperImpl(JsonMapper.builder().build()), flusher,
        executor, properties, meterRegistry);
  }

  @AfterEach
  void closeExecutor() {
    executor.close();
  }

  @Test
  void sendsOnAVirtualThreadWithRoutingKeyCorrelationAndDeterministicMessageId() {
    SendMoneyEvent event = EventFixtures.completedEvent(EventFixtures.newTxnId());
    CompletableFuture<Thread> sender = new CompletableFuture<>();
    onSend(correlation -> {
      sender.complete(Thread.currentThread());
      correlation.getFuture().complete(new Confirm(true, null));
    });

    publisher.publish(event);

    ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
    ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
    verify(rabbit, timeout(5_000)).send(eq("mfs.transactions"), eq("send-money.completed"),
        message.capture(),
        correlation.capture());
    assertThat(correlation.getValue().getId()).isEqualTo(event.txnId().toString());
    assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo(
        event.eventId().toString());

    Thread thread = sender.join();
    assertThat(thread.isVirtual()).isTrue();
    assertThat(thread.getName()).startsWith(AmqpConstants.EVENT_SEND_THREAD_PREFIX);
    assertThat(thread).isNotSameAs(Thread.currentThread());
  }

  @Test
  void ackBuffersTheTxnIdForThePublishMark() {
    SendMoneyEvent event = EventFixtures.completedEvent(EventFixtures.newTxnId());
    onSend(correlation -> correlation.getFuture().complete(new Confirm(true, null)));

    publisher.publish(event);

    await().atMost(Duration.ofSeconds(5)).until(() -> count(MetricConstants.RESULT_ACK) == 1);
    assertThat(buffer.drain(10)).containsExactly(event.txnId());
  }

  @Test
  void nackLeavesTheMarkNull() {
    onSend(correlation -> correlation.getFuture().complete(new Confirm(false, "internal error")));

    publisher.publish(EventFixtures.completedEvent(EventFixtures.newTxnId()));

    await().atMost(Duration.ofSeconds(5)).until(() -> count(MetricConstants.RESULT_NACK) == 1);
    assertThat(buffer.isEmpty()).isTrue();
    assertThat(count(MetricConstants.RESULT_ACK)).isZero();
  }

  @Test
  void returnedMessageIsNotMarkedEvenThoughTheBrokerAcked() {
    SendMoneyEvent event = EventFixtures.failedEvent(EventFixtures.newTxnId());
    onSend(correlation -> {
      correlation.setReturned(
          new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "mfs.transactions",
              "send-money.failed"));
      correlation.getFuture().complete(new Confirm(true, null));
    });

    publisher.publish(event);

    await().atMost(Duration.ofSeconds(5)).until(() -> count(MetricConstants.RESULT_RETURNED) == 1);
    assertThat(buffer.isEmpty()).isTrue();
    assertThat(count(MetricConstants.RESULT_ACK)).isZero();
  }

  @Test
  void missingConfirmTimesOut() {
    onSend(correlation -> {
      // the broker never confirms
    });

    publisher.publish(EventFixtures.completedEvent(EventFixtures.newTxnId()));

    await().atMost(Duration.ofSeconds(5)).until(() -> count(MetricConstants.RESULT_TIMEOUT) == 1);
    assertThat(buffer.isEmpty()).isTrue();
  }

  @Test
  void sendExceptionIsCountedAsError() {
    doThrow(new AmqpConnectException(new ConnectException("refused")))
        .when(rabbit)
        .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

    publisher.publish(EventFixtures.completedEvent(EventFixtures.newTxnId()));

    await().atMost(Duration.ofSeconds(5)).until(() -> count(MetricConstants.RESULT_ERROR) == 1);
    assertThat(buffer.isEmpty()).isTrue();
  }

  @Test
  void publishAfterShutdownIsCountedAndDoesNotThrow() {
    executor.close();

    publisher.publish(EventFixtures.completedEvent(EventFixtures.newTxnId()));

    assertThat(count(MetricConstants.RESULT_ERROR)).isEqualTo(1);
  }

  @Test
  void republishingTheSameEventUsesTheSameMessageId() {
    UUID txnId = EventFixtures.newTxnId();
    onSend(correlation -> correlation.getFuture().complete(new Confirm(true, null)));

    publisher.publish(EventFixtures.completedEvent(txnId));
    publisher.publish(EventFixtures.completedEvent(txnId));

    ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
    verify(rabbit, timeout(5_000).times(2)).send(anyString(), anyString(), messages.capture(),
        any(CorrelationData.class));
    assertThat(messages.getAllValues()).extracting(m -> m.getMessageProperties().getMessageId())
        .containsOnly(EventFixtures.completedEvent(txnId).eventId().toString());
  }

  private void onSend(Consumer<CorrelationData> broker) {
    doAnswer(invocation -> {
      broker.accept(invocation.getArgument(3, CorrelationData.class));
      return null;
    }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
  }

  private double count(String result) {
    return meterRegistry.counter(MetricConstants.EVENTS_PUBLISH, MetricConstants.TAG_RESULT, result)
        .count();
  }
}
