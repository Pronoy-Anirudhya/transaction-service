package com.bracits.transactionservice.application.fakes;

import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import com.bracits.transactionservice.port.out.repository.WalletRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wallets keyed by MSISDN; {@link #insertIfAbsent} honours the unique MSISDN and records the
 * limit-usage date.
 */
public final class InMemoryWalletRepository implements WalletRepository {

  private final Map<String, Wallet> byMsisdn = new HashMap<>();
  private final Map<Long, LocalDate> limitUsageDates = new HashMap<>();
  private final List<NewWallet> insertAttempts = new CopyOnWriteArrayList<>();
  private long nextId = 1_000L;

  /**
   * Adds or replaces a wallet (e.g. to freeze it).
   */
  public synchronized void put(Wallet wallet) {
    byMsisdn.values().removeIf(w -> w.walletId() == wallet.walletId());
    byMsisdn.put(wallet.msisdn(), wallet);
  }

  public synchronized void remove(long walletId) {
    byMsisdn.values().removeIf(w -> w.walletId() == walletId);
  }

  @Override
  public synchronized Optional<Wallet> findByMsisdn(String msisdn) {
    return Optional.ofNullable(byMsisdn.get(msisdn));
  }

  @Override
  public synchronized Optional<Wallet> findById(long walletId) {
    return byMsisdn.values().stream().filter(w -> w.walletId() == walletId).findFirst();
  }

  @Override
  public synchronized Optional<Wallet> insertIfAbsent(NewWallet wallet, LocalDate businessDate) {
    insertAttempts.add(wallet);
    if (byMsisdn.containsKey(wallet.msisdn())) {
      return Optional.empty();
    }

    Wallet stored = new Wallet(nextId++, wallet.msisdn(), wallet.holderName(), wallet.type(),
        wallet.status(),
        wallet.kycTier(), wallet.ledgerAccountId());
    byMsisdn.put(stored.msisdn(), stored);
    limitUsageDates.put(stored.walletId(), businessDate);
    return Optional.of(stored);
  }

  public synchronized Map<Long, LocalDate> limitUsageDates() {
    return Map.copyOf(limitUsageDates);
  }

  public List<NewWallet> insertAttempts() {
    return List.copyOf(insertAttempts);
  }

  public synchronized int size() {
    return byMsisdn.size();
  }
}
