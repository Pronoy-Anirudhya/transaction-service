package com.bracits.transactionservice.adapter.out.jdbc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcWalletRepositoryTest extends PostgresTestSupport {

  private static final LocalDate D = LocalDate.of(2026, 10, 8);

  @Test
  void insertStoresTheWalletAndCreatesItsUsageRow() {
    NewWallet wallet = newWallet();

    Wallet stored = wallets.insertIfAbsent(wallet, D).orElseThrow();

    assertThat(stored.walletId()).isPositive();
    assertThat(stored.msisdn()).isEqualTo(wallet.msisdn());
    assertThat(stored.holderName()).isEqualTo(wallet.holderName());
    assertThat(stored.type()).isEqualTo(wallet.type());
    assertThat(stored.status()).isEqualTo(wallet.status());
    assertThat(stored.kycTier()).isEqualTo(wallet.kycTier());
    assertThat(stored.ledgerAccountId()).isEqualTo(wallet.ledgerAccountId());
    assertThat(usage(stored.walletId())).isEqualTo(
        new Usage(D, 0, 0, LocalDate.of(2026, 10, 1), 0, 0));
  }

  @Test
  void duplicateMsisdnWritesNothingAndReturnsEmpty() {
    NewWallet wallet = newWallet();
    Wallet first = wallets.insertIfAbsent(wallet, D).orElseThrow();
    NewWallet sameMsisdn = new NewWallet(wallet.msisdn(), "Someone Else", wallet.type(),
        wallet.status(), 2,
        UUID.randomUUID());

    assertThat(wallets.insertIfAbsent(sameMsisdn, D)).isEmpty();
    assertThat(jdbc.sql("SELECT count(*) FROM wallet").query(Long.class).single()).isEqualTo(1L);
    assertThat(
        jdbc.sql("SELECT count(*) FROM wallet_limit_usage").query(Long.class).single()).isEqualTo(
        1L);
    assertThat(wallets.findByMsisdn(wallet.msisdn())).contains(first);
  }

  @Test
  void findsByMsisdnAndById() {
    Wallet stored = insertWallet(D);

    assertThat(wallets.findByMsisdn(stored.msisdn())).contains(stored);
    assertThat(wallets.findById(stored.walletId())).contains(stored);
    assertThat(wallets.findByMsisdn("01999999999")).isEmpty();
    assertThat(wallets.findById(999_999L)).isEmpty();
  }
}
