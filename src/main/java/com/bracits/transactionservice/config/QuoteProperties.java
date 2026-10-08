package com.bracits.transactionservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** {@code poc.quote.*}: quote token lifetime and HMAC signing key (FR-01). */
@Validated
@ConfigurationProperties(PropertyConstants.QUOTE)
public record QuoteProperties(@NotNull Duration ttl, @NotBlank String signingKey) {
}
