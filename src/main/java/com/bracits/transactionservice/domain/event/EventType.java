package com.bracits.transactionservice.domain.event;

import com.bracits.transactionservice.domain.TxnStatus;

/** Event types published to RabbitMQ (spec 9): payload name and routing key. */
public enum EventType {
  SEND_MONEY_COMPLETED("SendMoneyCompleted", "send-money.completed"),
  SEND_MONEY_FAILED("SendMoneyFailed", "send-money.failed");

  private final String eventName;
  private final String routingKey;

  EventType(String eventName, String routingKey) {
    this.eventName = eventName;
    this.routingKey = routingKey;
  }

  public String eventName() {
    return eventName;
  }

  public String routingKey() {
    return routingKey;
  }

  /** The event for a final status. INITIATED has no event. */
  public static EventType forFinalStatus(TxnStatus status) {
    return switch (status) {
      case COMPLETED -> SEND_MONEY_COMPLETED;
      case FAILED -> SEND_MONEY_FAILED;
      case INITIATED -> throw new IllegalArgumentException(status.name());
    };
  }
}
