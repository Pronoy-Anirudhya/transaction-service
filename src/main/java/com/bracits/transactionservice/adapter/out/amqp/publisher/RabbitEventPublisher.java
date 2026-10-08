package com.bracits.transactionservice.adapter.out.amqp.publisher;

import com.bracits.transactionservice.adapter.out.amqp.constant.AmqpConstants;
import com.bracits.transactionservice.adapter.out.amqp.executor.EventSendExecutor;
import com.bracits.transactionservice.adapter.out.amqp.mapper.EventMessageMapper;
import com.bracits.transactionservice.adapter.out.amqp.publishmark.PublishedMarkFlusher;
import com.bracits.transactionservice.config.constant.MetricConstants;
import com.bracits.transactionservice.config.properties.EventsProperties;
import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;
import com.bracits.transactionservice.port.out.publisher.EventPublisherPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.stereotype.Component;

/**
 * {@link EventPublisherPort} on RabbitMQ (spec 9, Adapter). {@link #publish} hands the event to a
 * virtual thread and returns at once; the send uses publisher confirms + mandatory, correlated by
 * txnId. Confirms are handled asynchronously with a timeout ({@code poc.events.confirm-timeout}):
 * ack (not returned) → the txnId goes to the batched publish mark; nack / returned / timeout /
 * error → logged and counted, the mark stays null and the republisher resends later. Bodies are
 * never logged.
 */
@Component
public final class RabbitEventPublisher implements EventPublisherPort {

  private static final Logger LOG = LoggerFactory.getLogger(RabbitEventPublisher.class);

  private final RabbitOperations rabbit;
  private final EventMessageMapper messageMapper;
  private final PublishedMarkFlusher markFlusher;
  private final EventSendExecutor executor;
  private final String exchange;
  private final long confirmTimeoutMillis;
  private final Counter acked;
  private final Counter nacked;
  private final Counter returned;
  private final Counter timedOut;
  private final Counter failed;

  public RabbitEventPublisher(
      RabbitOperations rabbit,
      EventMessageMapper messageMapper,
      PublishedMarkFlusher markFlusher,
      EventSendExecutor executor,
      EventsProperties properties,
      MeterRegistry meterRegistry) {
    this.rabbit = rabbit;
    this.messageMapper = messageMapper;
    this.markFlusher = markFlusher;
    this.executor = executor;
    this.exchange = properties.exchange();
    this.confirmTimeoutMillis = properties.confirmTimeout().toMillis();
    this.acked = counter(meterRegistry, MetricConstants.RESULT_ACK);
    this.nacked = counter(meterRegistry, MetricConstants.RESULT_NACK);
    this.returned = counter(meterRegistry, MetricConstants.RESULT_RETURNED);
    this.timedOut = counter(meterRegistry, MetricConstants.RESULT_TIMEOUT);
    this.failed = counter(meterRegistry, MetricConstants.RESULT_ERROR);

    warnIfConfirmsDisabled(rabbit.getConnectionFactory());
  }

  @Override
  public void publish(SendMoneyEvent event) {
    try {
      executor.execute(() -> send(event));
    } catch (RejectedExecutionException e) {
      failed.increment();
      LOG.warn(AmqpConstants.LOG_PUBLISH_REJECTED, event.txnId(), event.eventType().eventName());
    }
  }

  private void send(SendMoneyEvent event) {
    String routingKey = event.eventType().routingKey();
    CorrelationData correlation = new CorrelationData(event.txnId().toString());

    try {
      Message message = messageMapper.toMessage(event);
      rabbit.send(exchange, routingKey, message, correlation);
    } catch (RuntimeException e) {
      failed.increment();
      logNotConfirmed(MetricConstants.RESULT_ERROR, event, e.toString());
      return;
    }

    correlation.getFuture()
        .orTimeout(confirmTimeoutMillis, TimeUnit.MILLISECONDS)
        .whenComplete((confirm, error) -> onConfirm(event, correlation, confirm, error));
  }

  /**
   * Runs on the AMQP confirm thread or the timeout thread: only counts, logs and buffers (no I/O).
   */
  private void onConfirm(SendMoneyEvent event, CorrelationData correlation, Confirm confirm,
      Throwable error) {
    if (error != null) {
      Throwable cause =
          error instanceof CompletionException && error.getCause() != null ? error.getCause()
              : error;
      if (cause instanceof TimeoutException) {
        timedOut.increment();
        logNotConfirmed(MetricConstants.RESULT_TIMEOUT, event, cause.toString());
      } else {
        failed.increment();
        logNotConfirmed(MetricConstants.RESULT_ERROR, event, cause.toString());
      }

      return;
    }

    if (!confirm.ack()) {
      nacked.increment();
      logNotConfirmed(MetricConstants.RESULT_NACK, event,
          Objects.requireNonNullElse(confirm.reason(),
              AmqpConstants.NONE));
      return;
    }

    ReturnedMessage returnedMessage = correlation.getReturned();
    if (returnedMessage != null) {
      returned.increment();
      logNotConfirmed(MetricConstants.RESULT_RETURNED, event, returnedMessage.getReplyText());
      return;
    }

    acked.increment();
    markFlusher.onAck(event.txnId());
  }

  private void logNotConfirmed(String result, SendMoneyEvent event, String reason) {
    LOG.warn(AmqpConstants.LOG_PUBLISH_NOT_CONFIRMED, result, event.txnId(),
        event.eventType().eventName(),
        event.eventType().routingKey(), reason);
  }

  private static Counter counter(MeterRegistry registry, String result) {
    return Counter.builder(MetricConstants.EVENTS_PUBLISH)
        .tag(MetricConstants.TAG_RESULT, result)
        .register(registry);
  }

  private static void warnIfConfirmsDisabled(ConnectionFactory connectionFactory) {
    if (connectionFactory == null || !connectionFactory.isPublisherConfirms()
        || !connectionFactory.isPublisherReturns()) {
      LOG.warn(AmqpConstants.LOG_CONFIRMS_DISABLED);
    }
  }
}
