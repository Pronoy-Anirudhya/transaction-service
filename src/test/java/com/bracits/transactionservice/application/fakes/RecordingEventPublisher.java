package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.event.SendMoneyEvent;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.port.out.EventPublisherPort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Records published events and, through {@code lookup}, the stored status of the row at the moment of publishing. */
public final class RecordingEventPublisher implements EventPublisherPort {

  private final Function<UUID, Optional<SendMoneyTxn>> lookup;
  private final List<SendMoneyEvent> events = new CopyOnWriteArrayList<>();
  private final List<Optional<TxnStatus>> statusesAtPublish = new CopyOnWriteArrayList<>();

  public RecordingEventPublisher(Function<UUID, Optional<SendMoneyTxn>> lookup) {
    this.lookup = lookup;
  }

  @Override
  public void publish(SendMoneyEvent event) {
    events.add(event);
    statusesAtPublish.add(lookup.apply(event.txnId()).map(SendMoneyTxn::status));
  }

  public List<SendMoneyEvent> events() {
    return List.copyOf(events);
  }

  public List<Optional<TxnStatus>> statusesAtPublish() {
    return List.copyOf(statusesAtPublish);
  }
}
