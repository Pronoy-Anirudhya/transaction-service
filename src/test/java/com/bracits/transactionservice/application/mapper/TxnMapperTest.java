package com.bracits.transactionservice.application.mapper;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.application.fakes.TxnRowBuilder;
import com.bracits.transactionservice.application.result.TxnSummary;
import com.bracits.transactionservice.domain.FailureCode;
import com.bracits.transactionservice.domain.TxnStatus;
import com.bracits.transactionservice.domain.txn.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.RequestHash;
import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static com.bracits.transactionservice.application.fakes.Fixtures.AMOUNT;
import static com.bracits.transactionservice.application.fakes.Fixtures.BUSINESS_DATE;
import static com.bracits.transactionservice.application.fakes.Fixtures.CURRENCY;
import static com.bracits.transactionservice.application.fakes.Fixtures.KEY;
import static com.bracits.transactionservice.application.fakes.Fixtures.LEDGER_TS;
import static com.bracits.transactionservice.application.fakes.Fixtures.NOW;
import static com.bracits.transactionservice.application.fakes.Fixtures.RECEIVER_ID;
import static com.bracits.transactionservice.application.fakes.Fixtures.SENDER_ID;
import static com.bracits.transactionservice.application.fakes.Fixtures.STANDARD_PRICING;
import static com.bracits.transactionservice.application.fakes.Fixtures.command;
import static com.bracits.transactionservice.application.fakes.Fixtures.receiver;
import static com.bracits.transactionservice.application.fakes.Fixtures.sender;
import static com.bracits.transactionservice.application.fakes.Fixtures.withReference;
import static org.assertj.core.api.Assertions.assertThat;

class TxnMapperTest {

  private static final UUID TXN_ID = UUID.fromString("0192f5a4-1111-2222-3333-444444444400");
  private static final RequestHash HASH = new RequestHash(new byte[] {1, 2, 3});

  private final TxnMapper mapper = new TxnMapper();

  @Test
  void toNewTxnMapsEveryField() {
    NewSendMoneyTxn txn = mapper.toNewTxn(TXN_ID, command(), HASH, sender(), receiver(), STANDARD_PRICING,
        BUSINESS_DATE);

    assertThat(txn).isEqualTo(new NewSendMoneyTxn(TXN_ID, KEY, HASH, SENDER_ID, RECEIVER_ID, AMOUNT,
        STANDARD_PRICING, CURRENCY, "rent", BUSINESS_DATE));
  }

  @Test
  void absentReferenceBecomesNull() {
    SendMoneyCommand noReference = withReference(command(), Optional.empty());

    assertThat(mapper.toNewTxn(TXN_ID, noReference, HASH, sender(), receiver(), STANDARD_PRICING, BUSINESS_DATE)
        .reference()).isNull();
  }

  @Test
  void toSummaryOfACompletedRow() {
    SendMoneyTxn row = TxnRowBuilder.row(TXN_ID).completed(LEDGER_TS, NOW).build();

    assertThat(mapper.toSummary(row)).isEqualTo(new TxnSummary(TXN_ID, TxnStatus.COMPLETED, AMOUNT,
        STANDARD_PRICING, Optional.empty(), Optional.of(NOW)));
  }

  @Test
  void toSummaryOfAFailedRow() {
    SendMoneyTxn row = TxnRowBuilder.row(TXN_ID).failed(FailureCode.INSUFFICIENT_FUNDS, NOW).build();

    TxnSummary summary = mapper.toSummary(row);

    assertThat(summary.status()).isEqualTo(TxnStatus.FAILED);
    assertThat(summary.failureCode()).contains(FailureCode.INSUFFICIENT_FUNDS);
    assertThat(summary.totalDebit()).isEqualTo(100_500L);
  }

  @Test
  void toInitiatedSummaryOfAJustInsertedRow() {
    NewSendMoneyTxn txn = mapper.toNewTxn(TXN_ID, command(), HASH, sender(), receiver(), STANDARD_PRICING,
        BUSINESS_DATE);

    assertThat(mapper.toInitiatedSummary(txn)).isEqualTo(new TxnSummary(TXN_ID, TxnStatus.INITIATED, AMOUNT,
        STANDARD_PRICING, Optional.empty(), Optional.empty()));
  }
}
