package com.bracits.transactionservice.api.mapper;

import com.bracits.transactionservice.api.dto.ApiTxnStatus;
import com.bracits.transactionservice.api.dto.SendMoneyRequest;
import com.bracits.transactionservice.api.dto.SendMoneyResponse;
import com.bracits.transactionservice.api.dto.TxnStatusResponse;
import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.result.TxnSummary;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Send Money DTOs ↔ use-case command and results. */
@Component
public final class SendMoneyApiMapper {

  public SendMoneyCommand toCommand(SendMoneyRequest request, String idempotencyKey) {
    return new SendMoneyCommand(
        idempotencyKey,
        request.senderMsisdn(),
        request.receiverMsisdn(),
        request.amount(),
        request.currency(),
        Optional.ofNullable(request.reference()),
        Optional.ofNullable(request.quoteToken()));
  }

  public SendMoneyResponse toResponse(TxnSummary txn) {
    return new SendMoneyResponse(
        txn.txnId(),
        toApiStatus(txn.status()),
        txn.amount(),
        txn.pricing().fee(),
        txn.pricing().vat(),
        txn.pricing().commission(),
        txn.totalDebit(),
        txn.completedAt().orElse(null));
  }

  public TxnStatusResponse toStatusResponse(SendMoneyTxn txn) {
    return new TxnStatusResponse(
        txn.txnId(),
        toApiStatus(txn.status()),
        txn.amount(),
        txn.pricing().fee(),
        txn.pricing().vat(),
        txn.pricing().commission(),
        txn.totalDebit(),
        txn.currency(),
        txn.reference().orElse(null),
        txn.failureCode().map(FailureCode::name).orElse(null),
        txn.createdAt(),
        txn.completedAt().orElse(null));
  }

  public ApiTxnStatus toApiStatus(TxnStatus status) {
    return switch (status) {
      case INITIATED -> ApiTxnStatus.PROCESSING;
      case COMPLETED -> ApiTxnStatus.COMPLETED;
      case FAILED -> ApiTxnStatus.FAILED;
    };
  }
}
