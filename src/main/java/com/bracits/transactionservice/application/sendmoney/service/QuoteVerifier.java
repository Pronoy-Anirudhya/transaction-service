package com.bracits.transactionservice.application.sendmoney.service;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.domain.model.Pricing;
import java.util.Optional;

/**
 * Checks an optional quote token on a Send Money request (spec 7.1): it must be genuine (else 400),
 * unexpired, for this sender, receiver and amount, and quote today's fee (else 409
 * {@code QUOTE_CHANGED}, decision B4).
 */
public interface QuoteVerifier {

  /**
   * Empty when there is no token or it still holds; otherwise the result to answer with.
   */
  Optional<SendMoneyResult> verify(SendMoneyCommand command, Pricing pricing);
}
