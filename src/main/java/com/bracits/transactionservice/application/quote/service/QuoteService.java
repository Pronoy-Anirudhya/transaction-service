package com.bracits.transactionservice.application.quote.service;

import com.bracits.transactionservice.application.command.QuoteCommand;
import com.bracits.transactionservice.application.result.QuoteResult;

/**
 * FR-01: price a Send Money without any write and return a signed quote token valid for
 * {@code poc.quote.ttl}.
 */
public interface QuoteService {

  QuoteResult quote(QuoteCommand command);
}
