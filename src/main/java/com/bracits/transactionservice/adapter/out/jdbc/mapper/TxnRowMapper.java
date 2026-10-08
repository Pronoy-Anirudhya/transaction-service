package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.domain.txn.model.SendMoneyTxn;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@code send_money_txn} row → {@link SendMoneyTxn}; nullable columns become Optional /
 * OptionalLong.
 */
public interface TxnRowMapper extends RowMapper<SendMoneyTxn> {

}
