package com.bracits.transactionservice.adapter.out.amqp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Declares the topology as soon as the application is ready instead of on the first publish. Runs on a virtual thread
 * and never fails start-up: with RabbitMQ down (F9) the service still serves requests, and {@code RabbitAdmin}
 * declares the topology on the next successful connection.
 */
@Component
public final class RabbitTopologyInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(RabbitTopologyInitializer.class);

  private final AmqpAdmin amqpAdmin;

  public RabbitTopologyInitializer(AmqpAdmin amqpAdmin) {
    this.amqpAdmin = amqpAdmin;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void declareTopology() {
    Thread.ofVirtual().name(AmqpConstants.TOPOLOGY_INIT_THREAD_NAME).start(this::initialize);
  }

  private void initialize() {
    try {
      amqpAdmin.initialize();
    } catch (RuntimeException e) {
      LOG.warn(AmqpConstants.LOG_TOPOLOGY_INIT_FAILED, e.toString());
    }
  }
}
