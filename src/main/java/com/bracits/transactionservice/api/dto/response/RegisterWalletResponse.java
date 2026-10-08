package com.bracits.transactionservice.api.dto.response;

import java.util.UUID;

/**
 * 201 body of wallet registration.
 */
public record RegisterWalletResponse(long walletId, String msisdn, UUID ledgerAccountId) {

}
