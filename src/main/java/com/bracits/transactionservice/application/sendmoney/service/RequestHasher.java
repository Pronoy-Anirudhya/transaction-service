package com.bracits.transactionservice.application.sendmoney.service;

import com.bracits.transactionservice.application.command.SendMoneyCommand;
import com.bracits.transactionservice.domain.txn.model.RequestHash;

/**
 * {@code request_hash} = SHA-256 of the canonical request body (FR-03): every body field in a fixed
 * order, separated by a control character that cannot occur in valid input, with a distinct marker
 * for absent optional fields.
 */
public interface RequestHasher {

  RequestHash hash(SendMoneyCommand command);
}
