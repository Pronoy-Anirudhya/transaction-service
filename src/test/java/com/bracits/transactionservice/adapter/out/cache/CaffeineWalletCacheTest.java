package com.bracits.transactionservice.adapter.out.cache;

import com.bracits.transactionservice.domain.wallet.NewWallet;
import com.bracits.transactionservice.domain.wallet.Wallet;
import com.bracits.transactionservice.domain.wallet.WalletStatus;
import com.bracits.transactionservice.domain.wallet.WalletType;
import com.bracits.transactionservice.port.out.WalletRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineWalletCacheTest {

  private static final Duration TTL = Duration.ofSeconds(10);
  private static final String MSISDN = "01711000001";

  private final InMemoryWallets db = new InMemoryWallets();
  private final FakeTicker ticker = new FakeTicker();
  private final CaffeineWalletCache cache = new CaffeineWalletCache(db, TTL, 1_000, ticker);

  @Test
  void hitIsServedFromTheCache() {
    Wallet wallet = db.add(MSISDN);

    assertThat(cache.findByMsisdn(MSISDN)).contains(wallet);
    assertThat(cache.findByMsisdn(MSISDN)).contains(wallet);
    assertThat(db.msisdnReads.get()).isEqualTo(1);
  }

  @Test
  void missIsNotCached() {
    assertThat(cache.findByMsisdn(MSISDN)).isEmpty();
    Wallet wallet = db.add(MSISDN);

    assertThat(cache.findByMsisdn(MSISDN)).contains(wallet);
    assertThat(db.msisdnReads.get()).isEqualTo(2);
  }

  @Test
  void entryExpiresAfterTheTtl() {
    db.add(MSISDN);
    cache.findByMsisdn(MSISDN);

    ticker.advance(TTL.minusMillis(1));
    cache.findByMsisdn(MSISDN);
    assertThat(db.msisdnReads.get()).isEqualTo(1);

    ticker.advance(Duration.ofMillis(1));
    cache.findByMsisdn(MSISDN);
    assertThat(db.msisdnReads.get()).isEqualTo(2);
  }

  @Test
  void findByIdAndInsertDelegateWithoutCaching() {
    Wallet wallet = db.add(MSISDN);

    assertThat(cache.findById(wallet.walletId())).contains(wallet);
    assertThat(cache.findById(wallet.walletId())).contains(wallet);
    assertThat(db.idReads.get()).isEqualTo(2);

    NewWallet fresh = new NewWallet("01711000002", "B", WalletType.CUSTOMER, WalletStatus.ACTIVE, 1, UUID.randomUUID());
    assertThat(cache.insertIfAbsent(fresh, LocalDate.of(2026, 10, 8))).isPresent();
    assertThat(db.inserts.get()).isEqualTo(1);
  }

  /** In-memory fake of the JDBC repository that counts calls. */
  private static final class InMemoryWallets implements WalletRepository {

    private final Map<String, Wallet> byMsisdn = new HashMap<>();
    private final AtomicInteger msisdnReads = new AtomicInteger();
    private final AtomicInteger idReads = new AtomicInteger();
    private final AtomicInteger inserts = new AtomicInteger();

    Wallet add(String msisdn) {
      Wallet wallet = new Wallet(byMsisdn.size() + 1L, msisdn, "Holder", WalletType.CUSTOMER, WalletStatus.ACTIVE, 1,
          UUID.randomUUID());
      byMsisdn.put(msisdn, wallet);
      return wallet;
    }

    @Override
    public Optional<Wallet> findByMsisdn(String msisdn) {
      msisdnReads.incrementAndGet();
      return Optional.ofNullable(byMsisdn.get(msisdn));
    }

    @Override
    public Optional<Wallet> findById(long walletId) {
      idReads.incrementAndGet();
      return byMsisdn.values().stream().filter(w -> w.walletId() == walletId).findFirst();
    }

    @Override
    public Optional<Wallet> insertIfAbsent(NewWallet wallet, LocalDate businessDate) {
      inserts.incrementAndGet();
      return Optional.of(add(wallet.msisdn()));
    }
  }
}
