package com.bracits.transactionservice.adapter.out.cache;

import com.bracits.transactionservice.adapter.out.jdbc.JdbcWalletRepository;
import com.bracits.transactionservice.config.CacheProperties;
import com.bracits.transactionservice.domain.wallet.NewWallet;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.port.out.WalletRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Primary {@link WalletRepository} (P8): wallets by MSISDN in Caffeine ({@code poc.cache.wallet.ttl} after write,
 * at most {@code poc.cache.wallet.max-size} entries). Misses are not cached, so a wallet registered a moment ago is
 * found on the next read. {@link #findById} and {@link #insertIfAbsent} go straight to the database.
 */
@Primary
@Component
public final class CaffeineWalletCache implements WalletRepository {

  private final WalletRepository delegate;
  private final Cache<String, Wallet> byMsisdn;

  @Autowired
  public CaffeineWalletCache(JdbcWalletRepository delegate, CacheProperties properties) {
    this(delegate, properties.wallet().ttl(), properties.wallet().maxSize(), Ticker.systemTicker());
  }

  /** Test seam: any delegate and a controllable clock. */
  CaffeineWalletCache(WalletRepository delegate, Duration ttl, long maxSize, Ticker ticker) {
    this.delegate = delegate;
    this.byMsisdn = Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .maximumSize(maxSize)
        .ticker(ticker)
        .build();
  }

  @Override
  public Optional<Wallet> findByMsisdn(String msisdn) {
    // A null result of the loader is not stored: misses are never cached.
    return Optional.ofNullable(byMsisdn.get(msisdn, key -> delegate.findByMsisdn(key).orElse(null)));
  }

  @Override
  public Optional<Wallet> findById(long walletId) {
    return delegate.findById(walletId);
  }

  @Override
  public Optional<Wallet> insertIfAbsent(NewWallet wallet, LocalDate businessDate) {
    return delegate.insertIfAbsent(wallet, businessDate);
  }
}
