package com.bracits.transactionservice.application.result;

import java.util.UUID;

/**
 * Outcome of the test-profile funding use case.
 */
public sealed interface FundingResult {

  /**
   * 200: the funding is posted (or was already).
   */
  record Funded(UUID fundingId, String msisdn, long amount) implements FundingResult {

  }

  /**
   * 404: no wallet with that MSISDN (or the ledger does not know its account).
   */
  record WalletNotFound() implements FundingResult {

  }
}
