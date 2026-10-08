package com.bracits.transactionservice.config;

import com.bracits.transactionservice.adapter.out.amqp.AmqpConstants;
import com.bracits.transactionservice.adapter.out.amqp.EventSendExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology and publisher set-up (spec 9). The topology is declared idempotently by Spring Boot's
 * {@code RabbitAdmin} on every new connection (and eagerly at start-up by {@code RabbitTopologyInitializer}).
 * Publisher confirms and returns are enabled by {@code spring.rabbitmq.publisher-confirm-type=correlated} and
 * {@code spring.rabbitmq.publisher-returns=true}.
 */
@Configuration(proxyBeanMethods = false)
public class AmqpConfig {

  private static final Logger LOG = LoggerFactory.getLogger(AmqpConfig.class);

  /** Exchange {@code mfs.transactions} (topic), DLX, and the demo quorum queue bound to {@code send-money.#}. */
  @Bean(AmqpConstants.EVENTS_TOPOLOGY_BEAN)
  public Declarables eventsTopology(EventsProperties properties) {
    TopicExchange exchange = ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    TopicExchange deadLetterExchange =
        ExchangeBuilder.topicExchange(AmqpConstants.DEAD_LETTER_EXCHANGE).durable(true).build();
    Queue auditQueue = QueueBuilder.durable(AmqpConstants.AUDIT_QUEUE)
        .withArgument(AmqpConstants.ARG_QUEUE_TYPE, AmqpConstants.QUEUE_TYPE_QUORUM)
        .withArgument(AmqpConstants.ARG_DEAD_LETTER_EXCHANGE, AmqpConstants.DEAD_LETTER_EXCHANGE)
        .build();
    Binding binding = BindingBuilder.bind(auditQueue).to(exchange).with(AmqpConstants.SEND_MONEY_BINDING_PATTERN);
    return new Declarables(exchange, deadLetterExchange, auditQueue, binding);
  }

  /**
   * Mandatory publishing (unroutable messages are returned, spec 9 rule 3) and observation, so the W3C
   * {@code traceparent} header is propagated when tracing is on. Returns are counted by the publisher through
   * {@code CorrelationData#getReturned()}; this callback only logs at debug level.
   */
  @Bean(AmqpConstants.EVENTS_TEMPLATE_CUSTOMIZER_BEAN)
  public RabbitTemplateCustomizer eventsRabbitTemplateCustomizer() {
    return template -> {
      template.setMandatory(true);
      template.setObservationEnabled(true);
      template.setReturnsCallback(returned -> LOG.debug(AmqpConstants.LOG_RETURNED, returned.getExchange(),
          returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));
    };
  }

  /** Virtual-thread executor for event sends; closed (after draining in-flight sends) on shutdown. */
  @Bean(AmqpConstants.EVENT_SEND_EXECUTOR_BEAN)
  public EventSendExecutor eventSendExecutor() {
    return new EventSendExecutor(AmqpConstants.EVENT_SEND_THREAD_PREFIX);
  }
}
