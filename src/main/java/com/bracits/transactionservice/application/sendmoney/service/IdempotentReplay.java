package com.bracits.transactionservice.application.sendmoney.service;

import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Idempotent Receiver for Send Money (FR-03): the same {@code Idempotency-Key} with the same body
 * answers with the stored outcome; with a different body it is 409 {@code IDEMPOTENCY_CONFLICT}.
 */
public interface IdempotentReplay {

  /**
   * A request rejected before the insert may be a replay of a request that was accepted earlier
   * (decision B3): if a row exists for (sender, Idempotency-Key), answer from it; otherwise return
   * the rejection.
   */
  SendMoneyResult replayOr(
      Optional<Wallet> sender, String idempotencyKey, RequestHash hash,
      Supplier<SendMoneyResult> rejection);

  /**
   * The insert hit an existing (sender, Idempotency-Key): answer from that row.
   */
  SendMoneyResult replayExisting(long senderWalletId, String idempotencyKey,
      RequestHash hash);
}
