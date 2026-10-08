package com.bracits.transactionservice.application.sendmoney.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bracits.transactionservice.application.fakes.TxnRowBuilder;
import com.bracits.transactionservice.application.mapper.impl.TxnMapperImpl;
import com.bracits.transactionservice.application.result.SendMoneyResult;
import com.bracits.transactionservice.application.sendmoney.service.impl.IdempotentReplayImpl;
import com.bracits.transactionservice.domain.enums.FailureCode;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.domain.wallet.enums.WalletStatus;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class IdempotentReplayTest {

  private static final String KEY = "key-1";
  private static final UUID TXN_ID = UUID.fromString("01a11a3f-e349-d824-d395-04d2cb380100");
  private static final RequestHash SAME = new RequestHash(new byte[32]);
  private static final RequestHash OTHER = new RequestHash(new byte[]{1});
  private static final Wallet SENDER = new Wallet(
      7L, "01711000001", "Rahim Uddin", WalletType.CUSTOMER, WalletStatus.ACTIVE, 1,
      UUID.randomUUID());
  private static final SendMoneyResult REJECTION =
      new SendMoneyResult.Rejected(FailureCode.SELF_TRANSFER, Optional.empty());

  private final TxnRepository txns = mock(TxnRepository.class);
  private final IdempotentReplay replay = new IdempotentReplayImpl(txns, new TxnMapperImpl());

  @Test
  void unknownSenderReturnsTheRejectionWithoutALookup() {
    assertThat(replay.replayOr(Optional.empty(), KEY, SAME, () -> REJECTION)).isEqualTo(REJECTION);

    verifyNoInteractions(txns);
  }

  @Test
  void noStoredRowReturnsTheRejection() {
    given(txns.findBySenderAndClientRef(SENDER.walletId(), KEY)).willReturn(Optional.empty());

    assertThat(replay.replayOr(Optional.of(SENDER), KEY, SAME, () -> REJECTION)).isEqualTo(
        REJECTION);
  }

  @Test
  void sameBodyReplaysTheStoredOutcome() {
    SendMoneyTxn completed = TxnRowBuilder.row(TXN_ID).completed(1L, Instant.EPOCH).build();
    given(txns.findBySenderAndClientRef(SENDER.walletId(), KEY)).willReturn(Optional.of(completed));

    assertThat(replay.replayOr(Optional.of(SENDER), KEY, SAME, () -> REJECTION))
        .isInstanceOf(SendMoneyResult.Completed.class);
  }

  @Test
  void differentBodyIsAnIdempotencyConflict() {
    given(txns.findBySenderAndClientRef(anyLong(), anyString()))
        .willReturn(Optional.of(TxnRowBuilder.row(TXN_ID).build()));

    assertThat(replay.replayExisting(SENDER.walletId(), KEY, OTHER))
        .isEqualTo(
            new SendMoneyResult.Rejected(FailureCode.IDEMPOTENCY_CONFLICT, Optional.empty()));
  }

  @Test
  void lookupFailureFallsBackToTheRejection() {
    given(txns.findBySenderAndClientRef(anyLong(), anyString())).willThrow(
        new QueryTimeoutException("down"));

    assertThat(replay.replayOr(Optional.of(SENDER), KEY, SAME, () -> REJECTION)).isEqualTo(
        REJECTION);
  }
}
