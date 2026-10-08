package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.mapper.SendMoneyEventMapper;
import com.bracits.transactionservice.config.EventsProperties;
import com.bracits.transactionservice.config.PropertyConstants;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.port.out.EventPublisherPort;
import com.bracits.transactionservice.port.out.TxnRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Closes the gap between "row committed" and "broker confirmed" without an outbox (spec 9 rule 6, FR-07): every
 * {@code poc.events.republish.interval} (5 s) it claims up to {@code batch-size} (500) final rows whose
 * {@code event_published_at} is still null and older than {@code min-age} (10 s) — one auto-commit statement that
 * leases them ({@code next_check_at = now() + lease}, decision B9) — and publishes them again. The deterministic
 * {@code message_id} lets consumers de-duplicate. Delivery is at-least-once.
 */
@Component
public final class EventRepublisher {

  private static final Logger LOG = LoggerFactory.getLogger(EventRepublisher.class);

  private final TxnRepository txnRepository;
  private final EventPublisherPort eventPublisher;
  private final SendMoneyEventMapper eventMapper;
  private final EventsProperties.Republish settings;

  public EventRepublisher(
      TxnRepository txnRepository,
      EventPublisherPort eventPublisher,
      SendMoneyEventMapper eventMapper,
      EventsProperties properties) {
    this.txnRepository = txnRepository;
    this.eventPublisher = eventPublisher;
    this.eventMapper = eventMapper;
    this.settings = properties.republish();
  }

  @Scheduled(fixedDelayString = PropertyConstants.EVENTS_REPUBLISH_INTERVAL_PLACEHOLDER)
  public void scheduledRepublish() {
    republish();
  }

  /** @return number of rows claimed and handed to the publisher */
  public int republish() {
    List<SendMoneyTxn> claimed =
        txnRepository.claimUnpublished(settings.batchSize(), settings.minAge(), settings.lease());
    if (claimed.isEmpty()) {
      return 0;
    }
    LOG.info(ApplicationConstants.LOG_REPUBLISH_CLAIMED, claimed.size());
    for (SendMoneyTxn txn : claimed) {
      try {
        eventPublisher.publish(eventMapper.toEvent(txn));
      } catch (RuntimeException e) {
        LOG.warn(ApplicationConstants.LOG_REPUBLISH_FAILED, txn.txnId(), e.toString());
      }
    }
    return claimed.size();
  }
}
