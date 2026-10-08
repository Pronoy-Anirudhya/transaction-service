package com.bracits.transactionservice.application.event.service.impl;

import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.event.service.EventRepublisher;
import com.bracits.transactionservice.application.mapper.SendMoneyEventMapper;
import com.bracits.transactionservice.config.constant.PropertyConstants;
import com.bracits.transactionservice.config.properties.EventsProperties;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.port.out.publisher.EventPublisherPort;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link EventRepublisher}.
 */
@Component
public final class EventRepublisherImpl implements EventRepublisher {

  private static final Logger LOG = LoggerFactory.getLogger(EventRepublisherImpl.class);

  private final TxnRepository txnRepository;
  private final EventPublisherPort eventPublisher;
  private final SendMoneyEventMapper eventMapper;
  private final EventsProperties.Republish settings;

  public EventRepublisherImpl(
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
  @Override
  public void scheduledRepublish() {
    republish();
  }

  /**
   * @return number of rows claimed and handed to the publisher
   */
  @Override
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
