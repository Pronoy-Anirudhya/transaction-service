package com.bracits.transactionservice.config.properties;

import com.bracits.transactionservice.config.constant.PropertyConstants;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code poc.reconciliation.*}: row cap per call and concurrent ledger look-ups (FR-08).
 */
@Validated
@ConfigurationProperties(PropertyConstants.RECONCILIATION)
public record ReconciliationProperties(@Positive int maxRows, @Positive int parallelism) {

}
