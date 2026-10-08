package com.bracits.transactionservice.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

/** {@code poc.business.*}: the business-date zone (Asia/Dhaka) for limits (A3, NFR-10). */
@Validated
@ConfigurationProperties(PropertyConstants.BUSINESS)
public record BusinessProperties(@NotNull ZoneId zone) {
}
