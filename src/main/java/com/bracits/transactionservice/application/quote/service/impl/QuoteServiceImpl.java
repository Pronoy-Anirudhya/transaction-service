package com.bracits.transactionservice.application.quote.service.impl;

import com.bracits.transactionservice.application.command.QuoteCommand;
import com.bracits.transactionservice.application.quote.model.QuoteClaims;
import com.bracits.transactionservice.application.quote.service.QuoteService;
import com.bracits.transactionservice.application.quote.service.QuoteTokenCodec;
import com.bracits.transactionservice.application.result.PreparationResult;
import com.bracits.transactionservice.application.result.QuoteResult;
import com.bracits.transactionservice.application.sendmoney.service.SendMoneyPreparation;
import com.bracits.transactionservice.config.properties.QuoteProperties;
import com.bracits.transactionservice.domain.wallet.util.HolderNameMasker;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link QuoteService}.
 */
@Service
public class QuoteServiceImpl implements QuoteService {

  private final SendMoneyPreparation preparation;
  private final QuoteTokenCodec tokens;
  private final QuoteProperties properties;
  private final Clock clock;

  public QuoteServiceImpl(
      SendMoneyPreparation preparation, QuoteTokenCodec tokens, QuoteProperties properties,
      Clock clock) {
    this.preparation = preparation;
    this.tokens = tokens;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public QuoteResult quote(QuoteCommand command) {
    return switch (preparation.prepare(command.senderMsisdn(), command.receiverMsisdn(),
        command.amount())) {
      case PreparationResult.Failed failed -> new QuoteResult.Rejected(failed.code());
      case PreparationResult.Ready ready -> {
        Instant expiresAt = clock.instant().plus(properties.ttl());
        String token = tokens.encode(new QuoteClaims(
            command.senderMsisdn(), command.receiverMsisdn(), command.amount(),
            ready.pricing().fee(), expiresAt));
        yield new QuoteResult.Quoted(
            HolderNameMasker.mask(ready.receiver().holderName()), command.amount(), ready.pricing(),
            token, expiresAt);
      }
    };
  }
}
