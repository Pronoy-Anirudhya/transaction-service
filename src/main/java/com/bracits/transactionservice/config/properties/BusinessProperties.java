package com.bracits.transactionservice.config.properties;

import com.bracits.transactionservice.config.constant.PropertyConstants;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code poc.business.*}: the business-date zone (Asia/Dhaka) for limits (A3, NFR-10).
 */
@Validated
@ConfigurationProperties(PropertyConstants.BUSINESS)
public record BusinessProperties(@NotNull ZoneId zone) {

}
