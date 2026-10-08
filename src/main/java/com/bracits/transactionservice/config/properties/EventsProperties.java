package com.bracits.transactionservice.config.properties;

import com.bracits.transactionservice.config.constant.PropertyConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code poc.events.*}: event publishing (spec 9). {@code flushBatchSize} = flush the publish-mark
 * buffer early when it holds this many IDs (500).
 */
@Validated
@ConfigurationProperties(PropertyConstants.EVENTS)
public record EventsProperties(
    @NotBlank String exchange,
    @NotNull Duration confirmTimeout,
    @NotNull Duration flushInterval,
    @Positive int flushBatchSize,
    @NotNull @Valid Republish republish) {

  /**
   * Republisher: every 5 s, up to 500 rows, older than {@code minAge} (10 s), claimed for
   * {@code lease}.
   */
  public record Republish(
      @NotNull Duration interval,
      @Positive int batchSize,
      @NotNull Duration minAge,
      @NotNull Duration lease) {

  }
}
