package com.bracits.transactionservice.adapter.out.jdbc.repository;

import com.bracits.transactionservice.adapter.out.jdbc.mapper.impl.FeeRuleRowMapperImpl;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.impl.LimitParamMapperImpl;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.impl.LimitRuleRowMapperImpl;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.impl.TxnParamMapperImpl;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.impl.TxnRowMapperImpl;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.impl.WalletParamMapperImpl;
import com.bracits.transactionservice.adapter.out.jdbc.mapper.impl.WalletRowMapperImpl;
import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.domain.model.Pricing;
import com.bracits.transactionservice.domain.txn.model.NewSendMoneyTxn;
import com.bracits.transactionservice.domain.txn.model.RequestHash;
import com.bracits.transactionservice.domain.wallet.enums.WalletStatus;
import com.bracits.transactionservice.domain.wallet.enums.WalletType;
import com.bracits.transactionservice.domain.wallet.model.NewWallet;
import com.bracits.transactionservice.domain.wallet.model.Wallet;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 18 container per JVM (started lazily, removed by Ryuk), migrated once with Flyway.
 * Repositories are built by hand on {@link JdbcClient}; no Spring context. Mutable tables are
 * truncated before every test.
 */
abstract class PostgresTestSupport {

  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");
  private static final AtomicLong MSISDN_SEQ = new AtomicLong(1_700_000_000L);

  /**
   * The seeded SEND_MONEY tier-1 limits (spec 3).
   */
  static final LimitRule TIER1 = new LimitRule(Product.SEND_MONEY, 1, 1_000L, 2_500_000L,
      5_000_000L, 50, 30_000_000L, 200);

  static HikariDataSource dataSource;
  static JdbcClient jdbc;
  static TransactionTemplate tx;

  JdbcWalletRepository wallets;
  JdbcRuleRepository rules;
  JdbcLimitRepository limits;
  JdbcTxnRepository txns;

  @BeforeAll
  static synchronized void startDatabase() {
    if (dataSource != null) {
      return;
    }

    POSTGRES.start();

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(POSTGRES.getJdbcUrl());
    config.setUsername(POSTGRES.getUsername());
    config.setPassword(POSTGRES.getPassword());
    config.setMaximumPoolSize(8);
    dataSource = new HikariDataSource(config);

    Flyway.configure().dataSource(dataSource).load().migrate();

    jdbc = JdbcClient.create(dataSource);
    tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @BeforeEach
  void resetState() {
    jdbc.sql("TRUNCATE send_money_txn, wallet_limit_usage, wallet RESTART IDENTITY CASCADE")
        .update();

    wallets = new JdbcWalletRepository(jdbc, new WalletRowMapperImpl(),
        new WalletParamMapperImpl());
    rules = new JdbcRuleRepository(jdbc, new FeeRuleRowMapperImpl(), new LimitRuleRowMapperImpl());
    limits = new JdbcLimitRepository(jdbc, new LimitParamMapperImpl());
    txns = new JdbcTxnRepository(jdbc, tx, new TxnRowMapperImpl(), new TxnParamMapperImpl());
  }

  // ---- fixtures -------------------------------------------------------------------------------------------------

  static NewWallet newWallet() {
    return new NewWallet("01" + MSISDN_SEQ.incrementAndGet(), "Test Holder", WalletType.CUSTOMER,
        WalletStatus.ACTIVE, 1,
        UUID.randomUUID());
  }

  Wallet insertWallet(LocalDate businessDate) {
    return wallets.insertIfAbsent(newWallet(), businessDate).orElseThrow();
  }

  static NewSendMoneyTxn newTxn(long sender, long receiver, String clientRef, long amount,
      LocalDate businessDate) {
    return new NewSendMoneyTxn(UUID.randomUUID(), clientRef, hash(clientRef), sender, receiver,
        amount,
        new Pricing(500L, 65L, 87L, 348L), "BDT", null, businessDate);
  }

  static RequestHash hash(String text) {
    try {
      return new RequestHash(
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  // ---- direct reads / writes of wallet_limit_usage ------------------------------------------------------------

  record Usage(LocalDate day, long dayAmount, int dayCount, LocalDate month, long monthAmount,
               int monthCount) {

  }

  static Usage usage(long walletId) {
    return jdbc.sql("""
            SELECT day, day_amount, day_count, month, month_amount, month_count
              FROM wallet_limit_usage WHERE wallet_id = :id
            """)
        .param("id", walletId)
        .query((rs, n) -> new Usage(rs.getObject("day", LocalDate.class), rs.getLong("day_amount"),
            rs.getInt("day_count"), rs.getObject("month", LocalDate.class),
            rs.getLong("month_amount"),
            rs.getInt("month_count")))
        .single();
  }

  static void setUsage(long walletId, Usage u) {
    jdbc.sql("""
            UPDATE wallet_limit_usage SET day = :day, day_amount = :da, day_count = :dc,
                   month = :month, month_amount = :ma, month_count = :mc
             WHERE wallet_id = :id
            """)
        .param("day", u.day()).param("da", u.dayAmount()).param("dc", u.dayCount())
        .param("month", u.month()).param("ma", u.monthAmount()).param("mc", u.monthCount())
        .param("id", walletId)
        .update();
  }
}
