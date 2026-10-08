package com.bracits.transactionservice.port.out.publisher;

import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;

/**
 * Publishes final-status events to RabbitMQ (spec 9). {@link #publish} never blocks the caller: it
 * hands the event to a virtual thread and returns. On broker ack the txnId is queued for the
 * batched {@code event_published_at} mark; on nack / return / confirm timeout the mark stays null
 * and the republisher retries later.
 */
public interface EventPublisherPort {

  void publish(SendMoneyEvent event);
}
