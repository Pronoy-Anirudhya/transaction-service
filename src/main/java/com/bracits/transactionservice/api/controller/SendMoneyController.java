package com.bracits.transactionservice.api.controller;

import com.bracits.transactionservice.api.constant.ApiConstants;
import com.bracits.transactionservice.api.constant.ApiMessages;
import com.bracits.transactionservice.api.dto.request.SendMoneyRequest;
import com.bracits.transactionservice.api.dto.response.TxnStatusResponse;
import com.bracits.transactionservice.api.enums.ApiErrorCode;
import com.bracits.transactionservice.api.exception.ApiException;
import com.bracits.transactionservice.api.mapper.ProblemMapper;
import com.bracits.transactionservice.api.mapper.SendMoneyApiMapper;
import com.bracits.transactionservice.application.query.service.TxnQueryService;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.sendmoney.service.SendMoneyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-02 Send Money (200 COMPLETED / 202 PROCESSING / 409 / 422) and FR-04 status.
 */
@RestController
@RequestMapping(ApiConstants.SEND_MONEY_PATH)
public class SendMoneyController {

  private final SendMoneyService sendMoney;
  private final TxnQueryService queries;
  private final SendMoneyApiMapper mapper;
  private final ProblemMapper problems;

  public SendMoneyController(
      SendMoneyService sendMoney, TxnQueryService queries, SendMoneyApiMapper mapper,
      ProblemMapper problems) {
    this.sendMoney = sendMoney;
    this.queries = queries;
    this.mapper = mapper;
    this.problems = problems;
  }

  @PostMapping
  public ResponseEntity<?> send(
      @RequestHeader(ApiConstants.HEADER_IDEMPOTENCY_KEY)
      @NotBlank @Size(max = ApiConstants.IDEMPOTENCY_KEY_MAX_LENGTH) String idempotencyKey,
      @Valid @RequestBody SendMoneyRequest request) {
    SendMoneyResult result = sendMoney.send(mapper.toCommand(request, idempotencyKey));

    return switch (result) {
      case SendMoneyResult.Completed completed ->
          ResponseEntity.ok(mapper.toResponse(completed.txn()));
      case SendMoneyResult.Processing processing ->
          ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.toResponse(processing.txn()));
      case SendMoneyResult.Rejected rejected ->
          problems.toResponse(problems.toProblem(rejected.code(), rejected.txnId()));
      case SendMoneyResult.InvalidQuoteToken invalid -> problems.toResponse(
          problems.toProblem(ApiErrorCode.INVALID_QUOTE_TOKEN, ApiMessages.INVALID_QUOTE_TOKEN));
      case SendMoneyResult.LedgerUnavailable unavailable -> problems.toRetryLaterResponse(
          problems.toProblem(ApiErrorCode.LEDGER_UNAVAILABLE, ApiMessages.LEDGER_UNAVAILABLE));
    };
  }

  @GetMapping(ApiConstants.TXN_BY_ID_PATH)
  public TxnStatusResponse status(@PathVariable(ApiConstants.TXN_ID_VARIABLE) UUID txnId) {
    return queries.find(txnId)
        .map(mapper::toStatusResponse)
        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, ApiMessages.TXN_NOT_FOUND));
  }
}
