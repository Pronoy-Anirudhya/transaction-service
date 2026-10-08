package com.bracits.transactionservice.port.out.repository;

import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Wallets in {@code txn_db}. The primary bean is the Caffeine-cached adapter (P8).
 */
public interface WalletRepository {

  /**
   * Wallet by MSISDN (cached, TTL 10 s). Misses are not cached.
   */
  Optional<Wallet> findByMsisdn(String msisdn);

  /**
   * Wallet by ID (not cached; used by repair and reconciliation).
   */
  Optional<Wallet> findById(long walletId);

  /**
   * Inserts the wallet and its {@code wallet_limit_usage} row (day = {@code businessDate}, month =
   * its first day, counters 0) in ONE statement
   * ({@code WITH w AS (INSERT … ON CONFLICT (msisdn) DO NOTHING RETURNING *) …}).
   *
   * @return the stored wallet, or empty if the MSISDN already exists (nothing written)
   */
  Optional<Wallet> insertIfAbsent(NewWallet wallet, LocalDate businessDate);
}
