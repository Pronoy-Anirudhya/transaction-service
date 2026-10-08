package com.bracits.transactionservice.adapter.out.jdbc.mapper;

import com.bracits.transactionservice.domain.wallet.model.Wallet;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@code wallet} row → {@link Wallet}.
 */
public interface WalletRowMapper extends RowMapper<Wallet> {

}
