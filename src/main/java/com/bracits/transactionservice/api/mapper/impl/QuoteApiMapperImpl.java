package com.bracits.transactionservice.api.mapper.impl;

import com.bracits.transactionservice.api.dto.request.QuoteRequest;
import com.bracits.transactionservice.api.dto.response.QuoteResponse;
import com.bracits.transactionservice.api.mapper.QuoteApiMapper;
import com.bracits.transactionservice.application.command.QuoteCommand;
import com.bracits.transactionservice.application.result.QuoteResult;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link QuoteApiMapper}.
 */
@Component
public final class QuoteApiMapperImpl implements QuoteApiMapper {

  @Override
  public QuoteCommand toCommand(QuoteRequest request) {
    return new QuoteCommand(request.senderMsisdn(), request.receiverMsisdn(), request.amount(),
        request.currency());
  }

  @Override
  public QuoteResponse toResponse(QuoteResult.Quoted quoted) {
    return new QuoteResponse(
        quoted.receiverName(),
        quoted.amount(),
        quoted.pricing().fee(),
        quoted.pricing().vat(),
        quoted.pricing().commission(),
        quoted.totalDebit(),
        quoted.quoteToken(),
        quoted.expiresAt());
  }
}
