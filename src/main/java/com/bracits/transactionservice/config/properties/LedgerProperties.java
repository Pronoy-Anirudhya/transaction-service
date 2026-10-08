package com.bracits.transactionservice.config.properties;

import com.bracits.transactionservice.config.constant.PropertyConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code poc.ledger.*}: ledger client deadlines, retry policy, bulkhead and system account IDs
 * (spec 8.3, 12).
 */
@Validated
@ConfigurationProperties(PropertyConstants.LEDGER)
public record LedgerProperties(
    @NotNull URI baseUrl,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @NotNull Duration totalBudget,
    @PositiveOrZero int maxRetries,
    @Positive int concurrencyLimit,
    @NotNull Duration healthInterval,
    @NotNull @Valid Retry retry,
    @NotNull @Valid Accounts accounts) {

  /**
   * Backoff: {@code initialDelay} 50 ms, ×{@code multiplier} 2, ±{@code jitter} 20 ms.
   */
  public record Retry(@NotNull Duration initialDelay, @Positive double multiplier,
                      @NotNull Duration jitter) {

  }

  /**
   * Fixed system account IDs (spec 6.2).
   */
  public record Accounts(
      @NotNull UUID feeIncome,
      @NotNull UUID vatPayable,
      @NotNull UUID commissionPayable,
      @NotNull UUID issuance) {

  }
}
