package com.bracits.transactionservice.adapter.out.amqp.mapper;

import com.bracits.transactionservice.adapter.out.amqp.AmqpConstants;
import com.bracits.transactionservice.adapter.out.amqp.EventFixtures;
import com.bracits.transactionservice.domain.event.SendMoneyEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventMessageMapperTest {

  private final EventMessageMapper mapper = new EventMessageMapper(JsonMapper.builder().build());

  @Test
  void completedEventPayloadIsExactlyTheSpecShapeWithNullFailureCode() {
    SendMoneyEvent event = EventFixtures.completedEvent(EventFixtures.newTxnId());

    String json = new String(mapper.toBody(event), StandardCharsets.UTF_8);

    assertThat(json).isEqualTo(EventFixtures.expectedJson(event));
    assertThat(json).startsWith("{\"eventId\":\"" + event.eventId() + "\",\"eventType\":\"SendMoneyCompleted\"")
        .endsWith(",\"currency\":\"BDT\",\"ledgerTimestamp\":1791350858928000000,\"failureCode\":null}");
    assertThat(json.getBytes(StandardCharsets.UTF_8).length).isLessThan(512);
  }

  @Test
  void failedEventPayloadHasNullLedgerTimestampAndFailureCode() {
    SendMoneyEvent event = EventFixtures.failedEvent(EventFixtures.newTxnId());

    String json = new String(mapper.toBody(event), StandardCharsets.UTF_8);

    assertThat(json).isEqualTo(EventFixtures.expectedJson(event));
  }

  @Test
  void nullsAndIsoDatesSurviveGlobalJacksonSettings() {
    JsonMapper customised = JsonMapper.builder()
        .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
        .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();
    SendMoneyEvent event = EventFixtures.completedEvent(EventFixtures.newTxnId());

    String json = new String(new EventMessageMapper(customised).toBody(event), StandardCharsets.UTF_8);

    assertThat(json).isEqualTo(EventFixtures.expectedJson(event));
  }

  @Test
  void messagePropertiesArePersistentJsonWithDeterministicIdAndHeaders() {
    UUID txnId = EventFixtures.newTxnId();
    SendMoneyEvent event = EventFixtures.completedEvent(txnId);

    Message message = mapper.toMessage(event);
    MessageProperties properties = message.getMessageProperties();

    assertThat(properties.getMessageId()).isEqualTo(event.eventId().toString());
    assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(properties.getContentType()).isEqualTo("application/json");
    assertThat(properties.getHeaders())
        .containsEntry(AmqpConstants.HEADER_EVENT_TYPE, "SendMoneyCompleted")
        .containsEntry(AmqpConstants.HEADER_SCHEMA_VERSION, 1)
        .containsEntry(AmqpConstants.HEADER_TXN_ID, txnId.toString())
        .containsEntry(AmqpConstants.HEADER_OCCURRED_AT, "2026-10-07T09:14:03.211Z");
    assertThat(message.getBody()).isEqualTo(mapper.toBody(event));
  }

  @Test
  void payloadMirrorsTheEvent() {
    SendMoneyEvent event = EventFixtures.failedEvent(EventFixtures.newTxnId());

    var payload = mapper.toPayload(event);

    assertThat(payload.eventId()).isEqualTo(event.eventId());
    assertThat(payload.eventType()).isEqualTo("SendMoneyFailed");
    assertThat(payload.ledgerTimestamp()).isNull();
    assertThat(payload.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
  }
}
