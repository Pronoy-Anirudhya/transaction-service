package com.bracits.transactionservice.application.sendmoney.service.impl;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.quote.model.QuoteClaims;
import com.bracits.transactionservice.application.quote.model.QuoteTokenCheck;
import com.bracits.transactionservice.application.quote.service.QuoteTokenCodec;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.sendmoney.service.QuoteVerifier;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.model.Pricing;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link QuoteVerifier}.
 */
@Component
public final class QuoteVerifierImpl implements QuoteVerifier {

  private final QuoteTokenCodec quoteTokens;
  private final Clock clock;

  public QuoteVerifierImpl(QuoteTokenCodec quoteTokens, Clock clock) {
    this.quoteTokens = quoteTokens;
    this.clock = clock;
  }

  /**
   * Empty when there is no token or it still holds; otherwise the result to answer with.
   */
  @Override
  public Optional<SendMoneyResult> verify(SendMoneyCommand command, Pricing pricing) {
    if (command.quoteToken().isEmpty()) {
      return Optional.empty();
    }

    return switch (quoteTokens.decode(command.quoteToken().get())) {
      case QuoteTokenCheck.Invalid invalid -> Optional.of(new SendMoneyResult.InvalidQuoteToken());
      case QuoteTokenCheck.Valid valid -> unchanged(valid.claims(), command, pricing)
          ? Optional.empty()
          : Optional.of(new SendMoneyResult.Rejected(FailureCode.QUOTE_CHANGED, Optional.empty()));
    };
  }

  private boolean unchanged(QuoteClaims claims, SendMoneyCommand command, Pricing pricing) {
    boolean sameRequest = claims.matches(
        command.senderMsisdn(), command.receiverMsisdn(), command.amount(), clock.instant());
    return sameRequest && claims.fee() == pricing.fee();
  }
}
