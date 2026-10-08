package com.bracits.transactionservice.application.query.service.impl;

import com.bracits.transactionservice.application.query.service.TxnQueryService;
import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import com.bracits.transactionservice.port.out.repository.TxnRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link TxnQueryService}.
 */
@Service
public class TxnQueryServiceImpl implements TxnQueryService {

  private final TxnRepository txns;

  public TxnQueryServiceImpl(TxnRepository txns) {
    this.txns = txns;
  }

  @Override
  public Optional<SendMoneyTxn> find(UUID txnId) {
    return txns.findById(txnId);
  }
}
