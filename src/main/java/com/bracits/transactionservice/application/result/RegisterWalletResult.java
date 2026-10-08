package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.domain.wallet.model.Wallet;

/**
 * Outcome of test-profile wallet registration.
 */
public sealed interface RegisterWalletResult {

  /**
   * 201 when {@code created}, 200 for an identical replay. The ledger account exists in both
   * cases.
   */
  record Registered(Wallet wallet, boolean created) implements RegisterWalletResult {

  }

  /**
   * 409: the MSISDN exists with different fields.
   */
  record MsisdnExists() implements RegisterWalletResult {

  }
}
