package com.bracits.transactionservice.application.query.service;

import static com.bracits.transactionservice.application.fakes.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.application.fakes.InMemoryTxnRepository;
import com.bracits.transactionservice.application.fakes.MutableClock;
import com.bracits.transactionservice.application.fakes.TxnRowBuilder;
import com.bracits.transactionservice.application.query.service.impl.TxnQueryServiceImpl;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TxnQueryServiceTest {

  private final InMemoryTxnRepository txns = new InMemoryTxnRepository(new MutableClock(NOW));
  private final TxnQueryService service = new TxnQueryServiceImpl(txns);

  @Test
  void findsTheStoredRow() {
    SendMoneyTxn row = TxnRowBuilder.row(UUID.fromString("0192f5a4-1111-2222-3333-444444444400"))
        .build();
    txns.put(row);

    assertThat(service.find(row.txnId())).contains(row);
  }

  @Test
  void unknownTxnIdIsEmpty() {
    assertThat(service.find(UUID.fromString("0192f5a4-1111-2222-3333-444444444500"))).isEmpty();
  }
}
