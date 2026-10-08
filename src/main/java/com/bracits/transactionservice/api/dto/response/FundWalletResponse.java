package com.bracits.transactionservice.api.dto.response;

import java.util.UUID;

/**
 * 200 body of funding. {@code fundingId} is deterministic per (wallet, Idempotency-Key).
 */
public record FundWalletResponse(UUID fundingId, String msisdn, long amount, String status) {

}
