package com.bracits.transactionservice.api.mapper.impl;

import com.bracits.transactionservice.api.constant.ApiMessages;
import com.bracits.transactionservice.api.dto.request.SendMoneyRequest;
import com.bracits.transactionservice.api.dto.response.SendMoneyResponse;
import com.bracits.transactionservice.api.dto.response.TxnStatusResponse;
import com.bracits.transactionservice.api.enums.ApiTxnStatus;
import com.bracits.transactionservice.api.mapper.SendMoneyApiMapper;
import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.result.TxnSummary;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.enums.TxnStatus;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link SendMoneyApiMapper}.
 */
@Component
public final class SendMoneyApiMapperImpl implements SendMoneyApiMapper {

  @Override
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

  @Override
  public SendMoneyResponse toResponse(TxnSummary txn) {
    return new SendMoneyResponse(
        txn.txnId(),
        toApiStatus(txn.status()),
        txn.amount(),
        txn.pricing().fee(),
        txn.pricing().vat(),
        txn.pricing().commission(),
        txn.totalDebit(),
        txn.completedAt().orElse(null),
        messageFor(txn.status()));
  }

  @Override
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
        txn.completedAt().orElse(null),
        messageFor(txn.status()));
  }

  @Override
  public ApiTxnStatus toApiStatus(TxnStatus status) {
    return switch (status) {
      case INITIATED -> ApiTxnStatus.PROCESSING;
      case COMPLETED -> ApiTxnStatus.COMPLETED;
      case FAILED -> ApiTxnStatus.FAILED;
    };
  }

  /**
   * Only PROCESSING carries a message: it tells the client why there is no final answer yet.
   */
  private static String messageFor(TxnStatus status) {
    return status == TxnStatus.INITIATED ? ApiMessages.PROCESSING : null;
  }
}
