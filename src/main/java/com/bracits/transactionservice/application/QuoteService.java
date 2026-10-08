package com.bracits.transactionservice.application;

import com.bracits.transactionservice.application.SendMoneyPreparation.Prepared;
import com.bracits.transactionservice.application.command.QuoteCommand;
import com.bracits.transactionservice.application.quote.QuoteClaims;
import com.bracits.transactionservice.application.quote.QuoteTokenCodec;
import com.bracits.transactionservice.application.result.QuoteResult;
import com.bracits.transactionservice.config.QuoteProperties;
import com.bracits.transactionservice.domain.wallet.HolderNameMasker;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** FR-01: price a Send Money without any write and return a signed quote token valid for {@code poc.quote.ttl}. */
@Service
public class QuoteService {

  private final SendMoneyPreparation preparation;
  private final QuoteTokenCodec tokens;
  private final QuoteProperties properties;
  private final Clock clock;

  public QuoteService(
      SendMoneyPreparation preparation, QuoteTokenCodec tokens, QuoteProperties properties, Clock clock) {
    this.preparation = preparation;
    this.tokens = tokens;
    this.properties = properties;
    this.clock = clock;
  }

  public QuoteResult quote(QuoteCommand command) {
    return switch (preparation.prepare(command.senderMsisdn(), command.receiverMsisdn(), command.amount())) {
      case Prepared.Failed failed -> new QuoteResult.Rejected(failed.code());
      case Prepared.Ready ready -> {
        Instant expiresAt = clock.instant().plus(properties.ttl());
        String token = tokens.encode(new QuoteClaims(
            command.senderMsisdn(), command.receiverMsisdn(), command.amount(), ready.pricing().fee(), expiresAt));
        yield new QuoteResult.Quoted(
            HolderNameMasker.mask(ready.receiver().holderName()), command.amount(), ready.pricing(), token, expiresAt);
      }
    };
  }
}
