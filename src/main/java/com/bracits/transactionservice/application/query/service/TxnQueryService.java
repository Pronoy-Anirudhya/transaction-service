package com.bracits.transactionservice.application.query.service;

import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-04: the current state of one transaction, read from the primary (never cached).
 */
public interface TxnQueryService {

  Optional<SendMoneyTxn> find(UUID txnId);
}
