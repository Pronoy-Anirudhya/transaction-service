package com.bracits.transactionservice.config.properties;

import com.bracits.transactionservice.config.constant.PropertyConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code poc.cache.*}: Caffeine caches (P8).
 */
@Validated
@ConfigurationProperties(PropertyConstants.CACHE)
public record CacheProperties(@NotNull @Valid Wallet wallet, @NotNull @Valid Rules rules) {

  /**
   * Wallet by MSISDN: TTL 10 s, max 500 k entries.
   */
  public record Wallet(@NotNull Duration ttl, @Positive long maxSize) {

  }

  /**
   * Fee and limit rules: refreshed every 60 s.
   */
  public record Rules(@NotNull Duration refresh) {

  }
}
