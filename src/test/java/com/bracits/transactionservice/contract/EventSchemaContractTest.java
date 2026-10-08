package com.bracits.transactionservice.contract;

import com.bracits.transactionservice.adapter.out.amqp.EventFixtures;
import com.bracits.transactionservice.adapter.out.amqp.mapper.EventMessageMapper;
import com.bracits.transactionservice.domain.event.SendMoneyEvent;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The payload this service publishes validates against {@code openapi/events/send-money-v1.schema.json}. */
class EventSchemaContractTest {

  private static JsonSchema schema;
  private final EventMessageMapper mapper = new EventMessageMapper(JsonMapper.builder().build());

  @BeforeAll
  static void loadSchema() throws IOException {
    SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    try (InputStream in = new ClassPathResource("openapi/events/send-money-v1.schema.json").getInputStream()) {
      schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in, config);
    }
  }

  @Test
  void completedEventMatchesSchema() {
    assertThat(validate(EventFixtures.completedEvent(EventFixtures.newTxnId()))).isEmpty();
  }

  @Test
  void failedEventMatchesSchema() {
    assertThat(validate(EventFixtures.failedEvent(EventFixtures.newTxnId()))).isEmpty();
  }

  @Test
  void schemaRejectsUnknownAndMissingFields() {
    String json = body(EventFixtures.completedEvent(EventFixtures.newTxnId()));
    assertThat(schema.validate(json.replaceFirst("\\{", "{\"extra\":1,"), InputFormat.JSON)).isNotEmpty();
    assertThat(schema.validate(json.replaceFirst("\"currency\":\"BDT\",", ""), InputFormat.JSON)).isNotEmpty();
  }

  private Set<ValidationMessage> validate(SendMoneyEvent event) {
    return schema.validate(body(event), InputFormat.JSON);
  }

  private String body(SendMoneyEvent event) {
    return new String(mapper.toBody(event), StandardCharsets.UTF_8);
  }
}
