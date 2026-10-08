package com.bracits.transactionservice.config.properties;

import com.bracits.transactionservice.config.constant.PropertyConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code poc.quote.*}: quote token lifetime and HMAC signing key (FR-01).
 */
@Validated
@ConfigurationProperties(PropertyConstants.QUOTE)
public record QuoteProperties(@NotNull Duration ttl, @NotBlank String signingKey) {

}
