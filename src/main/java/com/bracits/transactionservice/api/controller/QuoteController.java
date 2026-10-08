package com.bracits.transactionservice.api.controller;

import com.bracits.transactionservice.api.ApiConstants;
import com.bracits.transactionservice.api.dto.QuoteRequest;
import com.bracits.transactionservice.api.mapper.ProblemMapper;
import com.bracits.transactionservice.api.mapper.QuoteApiMapper;
import com.bracits.transactionservice.application.QuoteService;
import com.bracits.transactionservice.application.result.QuoteResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/** FR-01 quote: price, masked receiver name and a signed quote token. No writes. */
@RestController
public class QuoteController {

  private final QuoteService quotes;
  private final QuoteApiMapper mapper;
  private final ProblemMapper problems;

  public QuoteController(QuoteService quotes, QuoteApiMapper mapper, ProblemMapper problems) {
    this.quotes = quotes;
    this.mapper = mapper;
    this.problems = problems;
  }

  @PostMapping(ApiConstants.QUOTE_PATH)
  public ResponseEntity<?> quote(@Valid @RequestBody QuoteRequest request) {
    return switch (quotes.quote(mapper.toCommand(request))) {
      case QuoteResult.Quoted quoted -> ResponseEntity.ok(mapper.toResponse(quoted));
      case QuoteResult.Rejected rejected -> problems.toResponse(problems.toProblem(rejected.code(), Optional.empty()));
    };
  }
}
