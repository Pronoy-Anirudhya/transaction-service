package com.bracits.transactionservice.adapter.out.ledger.health;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;
import com.bracits.transactionservice.port.out.client.LedgerHealthPort;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * The {@code ledger} component of {@code /actuator/health}. Informational only: it is not part of
 * the readiness group, because a ledger outage must not take this instance out of rotation (Send
 * Money answers 503 {@code LEDGER_UNAVAILABLE} instead, and quotes and status reads keep working).
 */
@Component
public final class LedgerHealthIndicator implements HealthIndicator {

  private final LedgerHealthPort ledger;

  public LedgerHealthIndicator(LedgerHealthPort ledger) {
    this.ledger = ledger;
  }

  @Override
  public Health health() {
    if (ledger.isAvailable()) {
      return Health.up().build();
    }
    return Health.down()
        .withDetail(LedgerApiConstants.HEALTH_DETAIL_REASON, LedgerApiConstants.MSG_LEDGER_DOWN)
        .build();
  }
}
