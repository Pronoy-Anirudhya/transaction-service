package com.bracits.transactionservice.adapter.out.amqp.mapper;

import com.bracits.transactionservice.adapter.out.amqp.dto.SendMoneyEventPayload;
import com.bracits.transactionservice.domain.event.model.SendMoneyEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/**
 * {@link SendMoneyEvent} to its AMQP message (spec 9): the JSON payload (Spring Boot's Jackson 3
 * mapper, UTF-8) and the message properties (persistent, deterministic {@code message_id},
 * {@code application/json}, headers).
 */
public interface EventMessageMapper {

  Message toMessage(SendMoneyEvent event);

  SendMoneyEventPayload toPayload(SendMoneyEvent event);

  /**
   * JSON bytes (UTF-8) of {@link #toPayload}.
   */
  byte[] toBody(SendMoneyEvent event);

  MessageProperties toMessageProperties(SendMoneyEvent event);
}
