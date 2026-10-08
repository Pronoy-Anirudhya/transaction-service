package com.bracits.transactionservice.api.dto.request;

import com.bracits.transactionservice.api.constant.ApiConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/wallets} (test profile): registers an ACTIVE CUSTOMER wallet.
 */
public record RegisterWalletRequest(
    @NotBlank @Pattern(regexp = ApiConstants.MSISDN_REGEX) String msisdn,
    @NotBlank @Size(max = ApiConstants.HOLDER_NAME_MAX_LENGTH) String holderName,
    @NotNull @Min(ApiConstants.MIN_KYC_TIER) Integer kycTier) {

}
