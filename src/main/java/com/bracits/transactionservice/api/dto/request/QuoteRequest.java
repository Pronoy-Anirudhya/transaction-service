package com.bracits.transactionservice.api.dto.request;

import com.bracits.transactionservice.api.constant.ApiConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * {@code POST /api/v1/send-money/quote} body.
 */
public record QuoteRequest(
    @NotBlank @Pattern(regexp = ApiConstants.MSISDN_REGEX) String senderMsisdn,
    @NotBlank @Pattern(regexp = ApiConstants.MSISDN_REGEX) String receiverMsisdn,
    @NotNull @Positive Long amount,
    @NotBlank @Pattern(regexp = ApiConstants.CURRENCY_REGEX) String currency) {

}
