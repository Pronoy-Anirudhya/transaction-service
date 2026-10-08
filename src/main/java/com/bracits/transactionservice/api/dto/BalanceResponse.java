package com.bracits.transactionservice.api.dto;

/** {@code GET /api/v1/wallets/{msisdn}/balance}: read through to the ledger (poisha). */
public record BalanceResponse(long posted, long pending, long available) {
}
