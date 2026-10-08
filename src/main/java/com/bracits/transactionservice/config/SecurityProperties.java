package com.bracits.transactionservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code poc.security.*}: the static API key protecting the public API (NFR-09). */
@Validated
@ConfigurationProperties(PropertyConstants.SECURITY)
public record SecurityProperties(@NotBlank String apiKey) {
}
