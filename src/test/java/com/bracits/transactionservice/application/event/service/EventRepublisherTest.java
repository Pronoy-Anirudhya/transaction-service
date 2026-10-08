package com.bracits.transactionservice.application.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bracits.transactionservice.adapter.out.amqp.fixture.EventFixtures;
import com.bracits.transactionservice.application.event.service.impl.EventRepublisherImpl;
import com.bracits.transactionservice.application.mapper.impl.SendMoneyEventMapperImpl;
import com.bracits.transactionservice.config.properties.EventsProperties;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.port.out.publisher.EventPublisherPort;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventRepublisherTest {

  private static final EventsProperties PROPERTIES = EventFixtures.properties(Duration.ofSeconds(5),
      500);

  private final TxnRepository txnRepository = mock(TxnRepository.class);
  private final RecordingPublisher publisher = new RecordingPublisher();
  private final EventRepublisher republisher =
      new EventRepublisherImpl(txnRepository, publisher, new SendMoneyEventMapperImpl(),
          PROPERTIES);

  @Test
  void claimsWithConfiguredBatchMinAgeAndLeaseAndRepublishesEachRow() {
    UUID completed = EventFixtures.newTxnId();
    UUID failed = EventFixtures.newTxnId();
    when(txnRepository.claimUnpublished(500, Duration.ofSeconds(10), Duration.ofSeconds(30)))
        .thenReturn(
            List.of(EventFixtures.completedTxn(completed), EventFixtures.failedTxn(failed)));

    int claimed = republisher.republish();

    assertThat(claimed).isEqualTo(2);
    assertThat(publisher.events).containsExactly(EventFixtures.completedEvent(completed),
        EventFixtures.failedEvent(failed));
    verify(txnRepository).claimUnpublished(500, Duration.ofSeconds(10), Duration.ofSeconds(30));
    verifyNoMoreInteractions(txnRepository);
  }

  @Test
  void republishCarriesTheSameEventIdAsTheOriginalPublish() {
    UUID txnId = EventFixtures.newTxnId();
    SendMoneyTxn row = EventFixtures.completedTxn(txnId);
    when(txnRepository.claimUnpublished(anyInt(), any(), any())).thenReturn(List.of(row));

    republisher.republish();

    assertThat(publisher.events.getFirst().eventId())
        .isEqualTo(new SendMoneyEventMapperImpl().toEvent(row).eventId());
  }

  @Test
  void nothingClaimedPublishesNothing() {
    when(txnRepository.claimUnpublished(anyInt(), any(), any())).thenReturn(List.of());

    assertThat(republisher.republish()).isZero();
    assertThat(publisher.events).isEmpty();
  }

  @Test
  void oneBadRowDoesNotStopTheBatch() {
    UUID good = EventFixtures.newTxnId();
    SendMoneyTxn initiated = EventFixtures.txn(EventFixtures.newTxnId(), TxnStatus.INITIATED,
        Optional.empty(),
        OptionalLong.empty(), Optional.empty());
    when(txnRepository.claimUnpublished(anyInt(), any(), any()))
        .thenReturn(List.of(initiated, EventFixtures.completedTxn(good)));

    assertThat(republisher.republish()).isEqualTo(2);
    assertThat(publisher.events).extracting(SendMoneyEvent::txnId).containsExactly(good);
  }

  @Test
  void scheduledEntryPointRepublishes() {
    UUID txnId = EventFixtures.newTxnId();
    when(txnRepository.claimUnpublished(anyInt(), any(), any()))
        .thenReturn(List.of(EventFixtures.completedTxn(txnId)));

    republisher.scheduledRepublish();

    assertThat(publisher.events).extracting(SendMoneyEvent::txnId).containsExactly(txnId);
  }

  private static final class RecordingPublisher implements EventPublisherPort {

    private final List<SendMoneyEvent> events = new ArrayList<>();

    @Override
    public void publish(SendMoneyEvent event) {
      events.add(event);
    }
  }
}
