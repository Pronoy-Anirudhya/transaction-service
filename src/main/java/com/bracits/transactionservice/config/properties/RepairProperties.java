package com.bracits.transactionservice.config.properties;

import com.bracits.transactionservice.config.constant.PropertyConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code poc.repair.*}: repair worker (spec 8.4). {@code minAge} = only rows older than this (3 s);
 * {@code unknownRecheck} = first recheck after an unknown outcome on the request path (2 s);
 * {@code initialBackoff} = first repair backoff step (1 s, doubling up to {@code maxBackoff});
 * {@code parallelism} = rows processed concurrently per claim.
 */
@Validated
@ConfigurationProperties(PropertyConstants.REPAIR)
public record RepairProperties(
    @NotNull Duration interval,
    @Positive int batchSize,
    @NotNull Duration lease,
    @NotNull Duration minAge,
    @NotNull Duration unknownRecheck,
    @NotNull Duration initialBackoff,
    @NotNull Duration maxBackoff,
    @Positive int alertAfterAttempts,
    @Positive int parallelism) {

}
