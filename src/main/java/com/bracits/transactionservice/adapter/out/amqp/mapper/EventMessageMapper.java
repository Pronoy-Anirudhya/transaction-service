package com.bracits.transactionservice.adapter.out.amqp.mapper;

import com.bracits.transactionservice.adapter.out.amqp.AmqpConstants;
import com.bracits.transactionservice.adapter.out.amqp.dto.SendMoneyEventPayload;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.event.SendMoneyEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.format.DateTimeFormatter;

/**
 * {@link SendMoneyEvent} to its AMQP message (spec 9): the JSON payload (Spring Boot's Jackson 3 mapper, UTF-8) and the
 * message properties (persistent, deterministic {@code message_id}, {@code application/json}, headers).
 */
@Component
public final class EventMessageMapper {

  private final JsonMapper jsonMapper;

  public EventMessageMapper(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public Message toMessage(SendMoneyEvent event) {
    return new Message(toBody(event), toMessageProperties(event));
  }

  public SendMoneyEventPayload toPayload(SendMoneyEvent event) {
    return new SendMoneyEventPayload(
        event.eventId(),
        event.eventType().eventName(),
        event.schemaVersion(),
        event.occurredAt(),
        event.txnId(),
        event.senderWalletId(),
        event.receiverWalletId(),
        event.amount(),
        event.fee(),
        event.vat(),
        event.commission(),
        event.feeIncome(),
        event.currency(),
        event.ledgerTimestamp().isPresent() ? event.ledgerTimestamp().getAsLong() : null,
        event.failureCode().map(FailureCode::name).orElse(null));
  }

  /** JSON bytes (UTF-8) of {@link #toPayload}. */
  public byte[] toBody(SendMoneyEvent event) {
    return jsonMapper.writeValueAsBytes(toPayload(event));
  }

  public MessageProperties toMessageProperties(SendMoneyEvent event) {
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(event.eventId().toString());
    properties.setContentType(AmqpConstants.CONTENT_TYPE_JSON);
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    properties.setHeader(AmqpConstants.HEADER_EVENT_TYPE, event.eventType().eventName());
    properties.setHeader(AmqpConstants.HEADER_SCHEMA_VERSION, event.schemaVersion());
    properties.setHeader(AmqpConstants.HEADER_TXN_ID, event.txnId().toString());
    properties.setHeader(AmqpConstants.HEADER_OCCURRED_AT, DateTimeFormatter.ISO_INSTANT.format(event.occurredAt()));
    return properties;
  }
}
