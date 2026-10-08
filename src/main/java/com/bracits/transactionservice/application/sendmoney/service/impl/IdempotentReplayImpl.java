package com.bracits.transactionservice.application.sendmoney.service.impl;

import com.bracits.transactionservice.application.constant.ApplicationConstants;
import com.bracits.transactionservice.application.mapper.TxnMapper;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.sendmoney.service.IdempotentReplay;
import com.bracits.transactionservice.config.constant.LogConstants;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link IdempotentReplay}.
 */
@Component
public final class IdempotentReplayImpl implements IdempotentReplay {

  private static final Logger LOG = LoggerFactory.getLogger(IdempotentReplayImpl.class);

  private final TxnRepository txns;
  private final TxnMapper txnMapper;

  public IdempotentReplayImpl(TxnRepository txns, TxnMapper txnMapper) {
    this.txns = txns;
    this.txnMapper = txnMapper;
  }

  /**
   * A request rejected before the insert may be a replay of a request that was accepted earlier
   * (decision B3): if a row exists for (sender, Idempotency-Key), answer from it; otherwise return
   * the rejection.
   */
  @Override
  public SendMoneyResult replayOr(
      Optional<Wallet> sender, String idempotencyKey, RequestHash hash,
      Supplier<SendMoneyResult> rejection) {
    if (sender.isEmpty()) {
      return rejection.get();
    }

    try {
      return txns.findBySenderAndClientRef(sender.get().walletId(), idempotencyKey)
          .map(row -> replay(row, hash))
          .orElseGet(rejection);
    } catch (DataAccessException e) {
      LOG.warn(ApplicationConstants.LOG_REPLAY_LOOKUP_FAILED);
      return rejection.get();
    }
  }

  /**
   * The insert hit an existing (sender, Idempotency-Key): answer from that row.
   */
  @Override
  public SendMoneyResult replayExisting(long senderWalletId, String idempotencyKey,
      RequestHash hash) {
    SendMoneyTxn row = txns.findBySenderAndClientRef(senderWalletId, idempotencyKey).orElseThrow();
    return replay(row, hash);
  }

  private SendMoneyResult replay(SendMoneyTxn row, RequestHash hash) {
    MDC.put(LogConstants.MDC_TXN_ID, row.txnId().toString());

    if (!row.requestHash().matches(hash)) {
      return new SendMoneyResult.Rejected(FailureCode.IDEMPOTENCY_CONFLICT, Optional.empty());
    }
    return txnMapper.toResult(row);
  }
}
