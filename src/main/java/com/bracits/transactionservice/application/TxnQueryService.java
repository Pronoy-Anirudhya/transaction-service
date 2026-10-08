package com.bracits.transactionservice.application;

import com.bracits.transactionservice.domain.txn.SendMoneyTxn;
import com.bracits.transactionservice.port.out.TxnRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/** FR-04: the current state of one transaction, read from the primary (never cached). */
@Service
public class TxnQueryService {

  private final TxnRepository txns;

  public TxnQueryService(TxnRepository txns) {
    this.txns = txns;
  }

  public Optional<SendMoneyTxn> find(UUID txnId) {
    return txns.findById(txnId);
  }
}
