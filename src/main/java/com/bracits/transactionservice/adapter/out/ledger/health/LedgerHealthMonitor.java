package com.bracits.transactionservice.adapter.out.ledger.health;

import com.bracits.transactionservice.adapter.out.ledger.constant.LedgerApiConstants;
import com.bracits.transactionservice.config.constant.PropertyConstants;
import com.bracits.transactionservice.port.out.client.LedgerHealthPort;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link LedgerHealthPort} backed by a periodic probe of ledger-service's readiness endpoint
 * ({@code GET /actuator/health/readiness}, one attempt, the ledger client's connect and read
 * timeouts). Availability flips only on the probe, so a single slow posting never closes the gate.
 */
@Component
public final class LedgerHealthMonitor implements LedgerHealthPort {

  private static final Logger LOG = LoggerFactory.getLogger(LedgerHealthMonitor.class);

  private final RestClient restClient;
  private final AtomicBoolean available = new AtomicBoolean(true);

  public LedgerHealthMonitor(
      @Qualifier(LedgerApiConstants.REST_CLIENT_BEAN) RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public boolean isAvailable() {
    return available.get();
  }

  /**
   * Re-probes the ledger and logs every transition (an outage is logged as an alert).
   */
  @Scheduled(fixedDelayString = PropertyConstants.LEDGER_HEALTH_INTERVAL_PLACEHOLDER)
  public void probe() {
    boolean up = isReady();

    boolean wasUp = available.getAndSet(up);
    if (wasUp && !up) {
      LOG.error(LedgerApiConstants.LOG_LEDGER_DOWN);
    } else if (!wasUp && up) {
      LOG.info(LedgerApiConstants.LOG_LEDGER_UP);
    }
  }

  private boolean isReady() {
    try {
      HttpStatusCode status = restClient.get()
          .uri(LedgerApiConstants.READINESS_PATH)
          .exchangeForRequiredValue((request, response) -> response.getStatusCode());
      return status.is2xxSuccessful();
    } catch (RestClientException e) {
      return false;
    }
  }
}
