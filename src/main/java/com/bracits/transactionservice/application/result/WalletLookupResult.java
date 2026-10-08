package com.bracits.transactionservice.application.result;

import com.bracits.transactionservice.domain.ledger.AccountBalance;

import java.util.UUID;

/** Outcomes of the funding and balance support use cases. */
public final class WalletLookupResult {

  private WalletLookupResult() {
  }

  /** Funding outcome. */
  public sealed interface Funding {
  }

  /** 200: the funding is posted (or was already). */
  public record Funded(UUID fundingId, String msisdn, long amount) implements Funding {
  }

  /** Balance outcome. */
  public sealed interface Balance {
  }

  /** 200: the ledger balance. */
  public record BalanceFound(AccountBalance balance) implements Balance {
  }

  /** 404: no wallet with that MSISDN (or the ledger does not know its account). */
  public record WalletNotFound() implements Funding, Balance {
  }
}
