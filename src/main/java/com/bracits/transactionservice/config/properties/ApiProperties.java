package com.bracits.transactionservice.config.properties;

import com.bracits.transactionservice.config.constant.PropertyConstants;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code poc.api.*}: bulkhead on the posting endpoint (P10).
 */
@Validated
@ConfigurationProperties(PropertyConstants.API)
public record ApiProperties(@Positive int sendMoneyConcurrencyLimit) {

}
