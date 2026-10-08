package com.bracits.transactionservice.adapter.out.ledger.health;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.web.client.RestClient;

class LedgerHealthMonitorTest {

  private static final String READINESS = "/actuator/health/readiness";

  private final WireMockServer ledger = new WireMockServer(options().dynamicPort());

  @AfterEach
  void stop() {
    ledger.stop();
  }

  @Test
  void readyLedgerIsAvailable() {
    ledger.start();
    ledger.stubFor(get(urlEqualTo(READINESS)).willReturn(okJson("{\"status\":\"UP\"}")));
    LedgerHealthMonitor monitor = monitorFor(ledger.baseUrl());

    monitor.probe();

    assertThat(monitor.isAvailable()).isTrue();
    assertThat(new LedgerHealthIndicator(monitor).health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void notReadyLedgerIsUnavailableAndRecovers() {
    ledger.start();
    ledger.stubFor(get(urlEqualTo(READINESS)).willReturn(aResponse().withStatus(503)));
    LedgerHealthMonitor monitor = monitorFor(ledger.baseUrl());

    monitor.probe();
    boolean whileDown = monitor.isAvailable();

    ledger.stubFor(get(urlEqualTo(READINESS)).willReturn(okJson("{\"status\":\"UP\"}")));
    monitor.probe();

    assertThat(whileDown).isFalse();
    assertThat(monitor.isAvailable()).isTrue();
  }

  @Test
  void unreachableLedgerIsUnavailable() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    LedgerHealthMonitor monitor = monitorFor("http://localhost:" + closedPort);

    monitor.probe();

    assertThat(monitor.isAvailable()).isFalse();
    assertThat(new LedgerHealthIndicator(monitor).health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void optimisticBeforeTheFirstProbe() {
    assertThat(monitorFor("http://localhost:1").isAvailable()).isTrue();
  }

  private static LedgerHealthMonitor monitorFor(String baseUrl) {
    return new LedgerHealthMonitor(RestClient.builder().baseUrl(baseUrl).build());
  }
}
