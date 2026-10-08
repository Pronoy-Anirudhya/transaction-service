package com.bracits.transactionservice.domain.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventIdsTest {

  private static final UUID DNS_NAMESPACE = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
  private static final UUID TXN_ID = UUID.fromString("0192f3a1-b2c3-4d5e-8f60-718293a4b500");

  @Test
  void knownVector() {
    assertThat(EventIds.uuidV5(DNS_NAMESPACE, "www.example.com"))
        .isEqualTo(UUID.fromString("2ed6657d-e927-568b-95e1-2665a8aea6a2"));
  }

  @Test
  void versionAndVariant() {
    UUID id = EventIds.of(TXN_ID, EventType.SEND_MONEY_COMPLETED);

    assertThat(id.version()).isEqualTo(5);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void eventIdIsDeterministic() {
    assertThat(EventIds.of(TXN_ID, EventType.SEND_MONEY_COMPLETED))
        .isEqualTo(EventIds.of(UUID.fromString(TXN_ID.toString()), EventType.SEND_MONEY_COMPLETED));
  }

  @Test
  void eventIdDependsOnTxnAndType() {
    UUID completed = EventIds.of(TXN_ID, EventType.SEND_MONEY_COMPLETED);

    assertThat(EventIds.of(TXN_ID, EventType.SEND_MONEY_FAILED)).isNotEqualTo(completed);
    assertThat(EventIds.of(new UUID(TXN_ID.getMostSignificantBits(), TXN_ID.getLeastSignificantBits() + 256),
        EventType.SEND_MONEY_COMPLETED)).isNotEqualTo(completed);
  }
}
