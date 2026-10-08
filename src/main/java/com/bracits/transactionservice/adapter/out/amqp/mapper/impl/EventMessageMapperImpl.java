package com.bracits.transactionservice.adapter.out.amqp.mapper.impl;

import com.bracits.transactionservice.adapter.out.amqp.constant.AmqpConstants;
import com.bracits.transactionservice.adapter.out.amqp.dto.SendMoneyEventPayload;
import com.bracits.transactionservice.adapter.out.amqp.mapper.EventMessageMapper;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;
import java.time.format.DateTimeFormatter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Default implementation of {@link EventMessageMapper}.
 */
@Component
public final class EventMessageMapperImpl implements EventMessageMapper {

  private final JsonMapper jsonMapper;

  public EventMessageMapperImpl(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Message toMessage(SendMoneyEvent event) {
    return new Message(toBody(event), toMessageProperties(event));
  }

  @Override
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

  /**
   * JSON bytes (UTF-8) of {@link #toPayload}.
   */
  @Override
  public byte[] toBody(SendMoneyEvent event) {
    return jsonMapper.writeValueAsBytes(toPayload(event));
  }

  @Override
  public MessageProperties toMessageProperties(SendMoneyEvent event) {
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(event.eventId().toString());
    properties.setContentType(AmqpConstants.CONTENT_TYPE_JSON);
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

    properties.setHeader(AmqpConstants.HEADER_EVENT_TYPE, event.eventType().eventName());
    properties.setHeader(AmqpConstants.HEADER_SCHEMA_VERSION, event.schemaVersion());
    properties.setHeader(AmqpConstants.HEADER_TXN_ID, event.txnId().toString());
    properties.setHeader(AmqpConstants.HEADER_OCCURRED_AT,
        DateTimeFormatter.ISO_INSTANT.format(event.occurredAt()));

    return properties;
  }
}
