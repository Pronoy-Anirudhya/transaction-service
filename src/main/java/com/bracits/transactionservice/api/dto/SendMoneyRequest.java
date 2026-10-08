package com.bracits.transactionservice.api.dto;

import com.bracits.transactionservice.api.ApiConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/send-money} body. Amount in poisha. {@code reference} and {@code quoteToken} are optional. */
public record SendMoneyRequest(
    @NotBlank @Pattern(regexp = ApiConstants.MSISDN_REGEX) String senderMsisdn,
    @NotBlank @Pattern(regexp = ApiConstants.MSISDN_REGEX) String receiverMsisdn,
    @NotNull @Positive Long amount,
    @NotBlank @Pattern(regexp = ApiConstants.CURRENCY_REGEX) String currency,
    @Size(max = ApiConstants.REFERENCE_MAX_LENGTH) String reference,
    @Size(max = ApiConstants.QUOTE_TOKEN_MAX_LENGTH) String quoteToken) {
}
