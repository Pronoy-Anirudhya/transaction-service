package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.port.out.client.LedgerHealthPort;

/**
 * Switchable ledger availability; available unless a test takes the ledger down.
 */
public final class FakeLedgerHealth implements LedgerHealthPort {

  private volatile boolean available = true;

  @Override
  public boolean isAvailable() {
    return available;
  }

  public void down() {
    available = false;
  }

  public void up() {
    available = true;
  }
}
