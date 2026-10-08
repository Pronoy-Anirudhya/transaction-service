package com.bracits.transactionservice.api.mapper;

import com.bracits.transactionservice.api.dto.request.QuoteRequest;
import com.bracits.transactionservice.api.dto.response.QuoteResponse;
import com.bracits.transactionservice.application.command.QuoteCommand;
import com.bracits.transactionservice.application.result.QuoteResult;

/**
 * Quote DTOs ↔ use-case command and result.
 */
public interface QuoteApiMapper {

  QuoteCommand toCommand(QuoteRequest request);

  QuoteResponse toResponse(QuoteResult.Quoted quoted);
}
