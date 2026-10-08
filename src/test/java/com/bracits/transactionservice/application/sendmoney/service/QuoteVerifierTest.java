package com.bracits.transactionservice.application.sendmoney.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.quote.model.QuoteClaims;
import com.bracits.transactionservice.application.quote.service.QuoteTokenCodec;
import com.bracits.transactionservice.application.quote.service.impl.QuoteTokenCodecImpl;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.sendmoney.service.impl.QuoteVerifierImpl;
import com.bracits.transactionservice.config.properties.QuoteProperties;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.model.Pricing;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QuoteVerifierTest {

  private static final Instant NOW = Instant.parse("2026-10-07T09:00:00Z");
  private static final Pricing PRICING = new Pricing(500, 65, 87, 348);
  private static final String SENDER = "01711000001";
  private static final String RECEIVER = "01811000002";

  private final QuoteTokenCodec codec = new QuoteTokenCodecImpl(
      new QuoteProperties(Duration.ofMinutes(5), "key"));
  private final QuoteVerifier verifier = new QuoteVerifierImpl(codec,
      Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void noTokenPasses() {
    assertThat(verifier.verify(command(Optional.empty()), PRICING)).isEmpty();
  }

  @Test
  void matchingUnexpiredTokenPasses() {
    assertThat(verifier.verify(command(token(500, NOW.plusSeconds(60))), PRICING)).isEmpty();
  }

  @Test
  void tamperedTokenIsInvalid() {
    assertThat(verifier.verify(command(Optional.of("garbage.token")), PRICING))
        .contains(new SendMoneyResult.InvalidQuoteToken());
  }

  @Test
  void changedFeeIsQuoteChanged() {
    assertThat(verifier.verify(command(token(400, NOW.plusSeconds(60))), PRICING))
        .contains(new SendMoneyResult.Rejected(FailureCode.QUOTE_CHANGED, Optional.empty()));
  }

  @Test
  void expiredTokenIsQuoteChanged() {
    assertThat(verifier.verify(command(token(500, NOW.minusSeconds(1))), PRICING))
        .contains(new SendMoneyResult.Rejected(FailureCode.QUOTE_CHANGED, Optional.empty()));
  }

  private Optional<String> token(long fee, Instant expiresAt) {
    return Optional.of(codec.encode(new QuoteClaims(SENDER, RECEIVER, 100_000, fee, expiresAt)));
  }

  private static SendMoneyCommand command(Optional<String> quoteToken) {
    return new SendMoneyCommand("key-1", SENDER, RECEIVER, 100_000, "BDT", Optional.empty(),
        quoteToken);
  }
}
