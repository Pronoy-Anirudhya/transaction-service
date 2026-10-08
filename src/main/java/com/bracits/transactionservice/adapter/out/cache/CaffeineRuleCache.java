package com.bracits.transactionservice.adapter.out.cache;

import com.bracits.transactionservice.adapter.out.cache.enums.RuleList;
import com.bracits.transactionservice.adapter.out.jdbc.constant.JdbcConstants;
import com.bracits.transactionservice.config.properties.CacheProperties;
import com.bracits.transactionservice.domain.fee.model.FeeRule;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.port.out.repository.RuleRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary {@link RuleRepository} (P8): each rule list is a single-key Caffeine {@link LoadingCache}
 * with {@code refreshAfterWrite(poc.cache.rules.refresh)}. After the first load, a stale entry is
 * still served while it is reloaded in the background, so the request path never waits for the
 * database; a failed reload keeps the old list.
 */
@Primary
@Component
public final class CaffeineRuleCache implements RuleRepository {

  private final LoadingCache<RuleList, List<FeeRule>> feeRules;
  private final LoadingCache<RuleList, List<LimitRule>> limitRules;

  @Autowired
  public CaffeineRuleCache(
      @Qualifier(JdbcConstants.JDBC_RULE_REPOSITORY_BEAN) RuleRepository delegate,
      CacheProperties properties) {
    this(delegate, properties.rules().refresh(), Ticker.systemTicker(), Thread::startVirtualThread);
  }

  /**
   * Test seam: any delegate, a controllable clock and the executor that runs background reloads.
   */
  CaffeineRuleCache(RuleRepository delegate, Duration refresh, Ticker ticker, Executor executor) {
    this.feeRules = Caffeine.newBuilder()
        .refreshAfterWrite(refresh)
        .ticker(ticker)
        .executor(executor)
        .build(key -> List.copyOf(delegate.findActiveFeeRules()));

    this.limitRules = Caffeine.newBuilder()
        .refreshAfterWrite(refresh)
        .ticker(ticker)
        .executor(executor)
        .build(key -> List.copyOf(delegate.findLimitRules()));
  }

  @Override
  public List<FeeRule> findActiveFeeRules() {
    return feeRules.get(RuleList.ALL);
  }

  @Override
  public List<LimitRule> findLimitRules() {
    return limitRules.get(RuleList.ALL);
  }
}
