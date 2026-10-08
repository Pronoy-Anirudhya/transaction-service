package com.bracits.transactionservice.adapter.out.jdbc;

import com.bracits.transactionservice.adapter.out.cache.CaffeineRuleCache;
import com.bracits.transactionservice.adapter.out.cache.CaffeineWalletCache;
import com.bracits.transactionservice.config.CacheProperties;
import com.bracits.transactionservice.port.out.LimitRepository;
import com.bracits.transactionservice.port.out.RuleRepository;
import com.bracits.transactionservice.port.out.TxnRepository;
import com.bracits.transactionservice.port.out.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adapters wire as Spring beans the way Spring Boot sets them up, including Boot's class-based persistence
 * exception translation post-processor: the Caffeine decorators are the primary port beans and get the concrete JDBC
 * repositories injected.
 */
@Testcontainers(disabledWithoutDocker = true)
class PersistenceWiringTest extends PostgresTestSupport {

  @Test
  void cachesArePrimaryAndDecorateTheJdbcRepositories() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(JdbcClient.class, () -> jdbc);
      context.registerBean(TransactionOperations.class, () -> tx);
      context.registerBean(CacheProperties.class, () -> new CacheProperties(
          new CacheProperties.Wallet(Duration.ofSeconds(10), 500_000L),
          new CacheProperties.Rules(Duration.ofSeconds(60))));
      context.registerBean(PersistenceExceptionTranslationPostProcessor.class, () -> {
        PersistenceExceptionTranslationPostProcessor processor = new PersistenceExceptionTranslationPostProcessor();
        processor.setProxyTargetClass(true);
        return processor;
      });
      context.scan("com.bracits.transactionservice.adapter.out.jdbc", "com.bracits.transactionservice.adapter.out.cache");
      context.refresh();

      assertThat(context.getBean(WalletRepository.class)).isInstanceOf(CaffeineWalletCache.class);
      assertThat(context.getBean(RuleRepository.class)).isInstanceOf(CaffeineRuleCache.class);
      assertThat(context.getBean(TxnRepository.class)).isInstanceOf(JdbcTxnRepository.class);
      assertThat(context.getBean(LimitRepository.class)).isInstanceOf(JdbcLimitRepository.class);
      assertThat(context.getBean(RuleRepository.class).findLimitRules()).containsExactly(TIER1);
      assertThat(context.getBean(TxnRepository.class).countInDoubt()).isZero();
    }
  }
}
