package com.bracits.transactionservice.api.mapper;

import com.bracits.transactionservice.api.dto.request.SendMoneyRequest;
import com.bracits.transactionservice.api.dto.response.SendMoneyResponse;
import com.bracits.transactionservice.api.dto.response.TxnStatusResponse;
import com.bracits.transactionservice.api.enums.ApiTxnStatus;
import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.result.TxnSummary;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;

/**
 * Send Money DTOs ↔ use-case command and results.
 */
public interface SendMoneyApiMapper {

  SendMoneyCommand toCommand(SendMoneyRequest request, String idempotencyKey);

  SendMoneyResponse toResponse(TxnSummary txn);

  TxnStatusResponse toStatusResponse(SendMoneyTxn txn);

  ApiTxnStatus toApiStatus(TxnStatus status);
}
