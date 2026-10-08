package com.bracits.transactionservice.adapter.out.amqp.constant;

import org.springframework.amqp.core.MessageProperties;

/**
 * RabbitMQ topology, message headers, bean names and log messages of the event adapter (spec 9).
 */
public final class AmqpConstants {

  // Topology (the main exchange name comes from poc.events.exchange)
  /**
   * Dead-letter exchange (topic, durable).
   */
  public static final String DEAD_LETTER_EXCHANGE = "mfs.transactions.dlx";
  /**
   * Demo quorum queue, for tests and demos only.
   */
  public static final String AUDIT_QUEUE = "audit.send-money";
  /**
   * Binding of the demo queue: every Send Money event.
   */
  public static final String SEND_MONEY_BINDING_PATTERN = "send-money.#";

  // Queue arguments
  public static final String ARG_QUEUE_TYPE = "x-queue-type";
  public static final String QUEUE_TYPE_QUORUM = "quorum";
  public static final String ARG_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";

  // Message headers and properties
  public static final String HEADER_EVENT_TYPE = "event-type";
  public static final String HEADER_SCHEMA_VERSION = "schema-version";
  public static final String HEADER_TXN_ID = "txn-id";
  public static final String HEADER_OCCURRED_AT = "occurred-at";
  /**
   * W3C trace context; written by the RabbitTemplate observation when tracing is on.
   */
  public static final String HEADER_TRACEPARENT = "traceparent";
  public static final String CONTENT_TYPE_JSON = MessageProperties.CONTENT_TYPE_JSON;

  // Bean names
  public static final String EVENTS_TOPOLOGY_BEAN = "eventsTopology";
  public static final String EVENTS_TEMPLATE_CUSTOMIZER_BEAN = "eventsRabbitTemplateCustomizer";
  public static final String EVENT_SEND_EXECUTOR_BEAN = "eventSendExecutor";

  // Thread names
  public static final String EVENT_SEND_THREAD_PREFIX = "event-send-";
  public static final String MARK_FLUSH_THREAD_NAME = "publish-mark-flush";
  public static final String TOPOLOGY_INIT_THREAD_NAME = "amqp-topology-init";

  // Log messages (never message bodies)
  public static final String LOG_PUBLISH_NOT_CONFIRMED =
      "event not confirmed: result={} txnId={} eventType={} routingKey={} reason={}";
  public static final String LOG_PUBLISH_REJECTED = "event hand-off rejected (shutting down?): txnId={} eventType={}";
  public static final String LOG_RETURNED =
      "event returned by broker: exchange={} routingKey={} replyCode={} replyText={}";
  public static final String LOG_CONFIRMS_DISABLED =
      "publisher confirms or returns are disabled on the RabbitMQ connection factory; events will never be marked "
          + "published (set spring.rabbitmq.publisher-confirm-type=correlated and spring.rabbitmq.publisher-returns=true)";
  public static final String LOG_MARK_FLUSH_FAILED =
      "publish-mark flush of {} txnIds failed; dropped (the republisher will resend them): {}";
  public static final String LOG_TOPOLOGY_INIT_FAILED =
      "RabbitMQ topology could not be declared at start-up; it is declared on the next connection: {}";
  public static final String NONE = "none";

  private AmqpConstants() {
  }
}
