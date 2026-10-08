package com.bracits.transactionservice.api.mapper;

import com.bracits.transactionservice.api.dto.QuoteRequest;
import com.bracits.transactionservice.api.dto.QuoteResponse;
import com.bracits.transactionservice.application.command.QuoteCommand;
import com.bracits.transactionservice.application.result.QuoteResult;
import org.springframework.stereotype.Component;

/** Quote DTOs ↔ use-case command and result. */
@Component
public final class QuoteApiMapper {

  public QuoteCommand toCommand(QuoteRequest request) {
    return new QuoteCommand(request.senderMsisdn(), request.receiverMsisdn(), request.amount(), request.currency());
  }

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
