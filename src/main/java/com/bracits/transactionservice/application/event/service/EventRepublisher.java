package com.bracits.transactionservice.application.event.service;


/**
 * Closes the gap between "row committed" and "broker confirmed" without an outbox (spec 9 rule 6,
 * FR-07): every {@code poc.events.republish.interval} (5 s) it claims up to {@code batch-size}
 * (500) final rows whose {@code event_published_at} is still null and older than {@code min-age}
 * (10 s) — one auto-commit statement that leases them ({@code next_check_at = now() + lease},
 * decision B9) — and publishes them again. The deterministic {@code message_id} lets consumers
 * de-duplicate. Delivery is at-least-once.
 */
public interface EventRepublisher {

  void scheduledRepublish();

  /**
   * @return number of rows claimed and handed to the publisher
   */
  int republish();
}
