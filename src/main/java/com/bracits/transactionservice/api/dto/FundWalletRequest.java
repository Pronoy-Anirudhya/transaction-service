package com.bracits.transactionservice.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** {@code POST /api/v1/wallets/{msisdn}/fund} (test profile): simulated cash-in from the issuance account. */
public record FundWalletRequest(@NotNull @Positive Long amount) {
}
