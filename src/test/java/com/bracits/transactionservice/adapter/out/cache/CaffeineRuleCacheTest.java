package com.bracits.transactionservice.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.transactionservice.domain.enums.Product;
import com.bracits.transactionservice.domain.fee.enums.FeeType;
import com.bracits.transactionservice.domain.fee.model.FeeRule;
import com.bracits.transactionservice.domain.limit.model.LimitRule;
import com.bracits.transactionservice.port.out.repository.RuleRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CaffeineRuleCacheTest {

  private static final Duration REFRESH = Duration.ofSeconds(60);
  private static final FeeRule FEE_V1 = fee(500L);
  private static final FeeRule FEE_V2 = fee(700L);
  private static final LimitRule LIMIT_V1 = limit(50);
  private static final LimitRule LIMIT_V2 = limit(60);

  private final FakeRules db = new FakeRules();
  private final FakeTicker ticker = new FakeTicker();
  private final List<Runnable> pendingReloads = new ArrayList<>();
  private final Executor deferred = pendingReloads::add;

  @Test
  void loadsOnceAndServesFromTheCache() {
    CaffeineRuleCache cache = new CaffeineRuleCache(db, REFRESH, ticker, Runnable::run);

    assertThat(cache.findActiveFeeRules()).containsExactly(FEE_V1);
    assertThat(cache.findActiveFeeRules()).containsExactly(FEE_V1);
    assertThat(cache.findLimitRules()).containsExactly(LIMIT_V1);
    assertThat(cache.findLimitRules()).containsExactly(LIMIT_V1);
    assertThat(db.feeReads.get()).isEqualTo(1);
    assertThat(db.limitReads.get()).isEqualTo(1);
  }

  @Test
  void refreshesAfterTheIntervalWithoutMakingTheCallerWait() {
    CaffeineRuleCache cache = new CaffeineRuleCache(db, REFRESH, ticker, deferred);
    runPending(); // the executor also runs Caffeine maintenance

    assertThat(cache.findActiveFeeRules()).containsExactly(FEE_V1);
    assertThat(cache.findLimitRules()).containsExactly(LIMIT_V1);

    db.fee = FEE_V2;
    db.limit = LIMIT_V2;

    ticker.advance(REFRESH.plusSeconds(1));
    // the stale lists are returned at once; the reload is only scheduled
    assertThat(cache.findActiveFeeRules()).containsExactly(FEE_V1);
    assertThat(cache.findLimitRules()).containsExactly(LIMIT_V1);

    runPending();
    assertThat(cache.findActiveFeeRules()).containsExactly(FEE_V2);
    assertThat(cache.findLimitRules()).containsExactly(LIMIT_V2);
    assertThat(db.feeReads.get()).isEqualTo(2);
  }

  @Test
  void failedReloadKeepsTheOldRules() {
    CaffeineRuleCache cache = new CaffeineRuleCache(db, REFRESH, ticker, Runnable::run);
    cache.findActiveFeeRules();
    db.failing.set(true);

    ticker.advance(REFRESH.plusSeconds(1));

    assertThat(cache.findActiveFeeRules()).containsExactly(FEE_V1);
    assertThat(cache.findActiveFeeRules()).containsExactly(FEE_V1);
  }

  private void runPending() {
    while (!pendingReloads.isEmpty()) {
      pendingReloads.removeFirst().run();
    }
  }

  private static FeeRule fee(long value) {
    return new FeeRule(1L, Product.SEND_MONEY, 1, 10_001L, 2_500_000L, FeeType.FLAT, value, 0L,
        OptionalLong.empty(),
        1_500, 2_000, true);
  }

  private static LimitRule limit(int dailyCount) {
    return new LimitRule(Product.SEND_MONEY, 1, 1_000L, 2_500_000L, 5_000_000L, dailyCount,
        30_000_000L, 200);
  }

  private static final class FakeRules implements RuleRepository {

    private final AtomicInteger feeReads = new AtomicInteger();
    private final AtomicInteger limitReads = new AtomicInteger();
    private final AtomicBoolean failing = new AtomicBoolean();
    private volatile FeeRule fee = FEE_V1;
    private volatile LimitRule limit = LIMIT_V1;

    @Override
    public List<FeeRule> findActiveFeeRules() {
      feeReads.incrementAndGet();
      if (failing.get()) {
        throw new IllegalStateException("database down");
      }

      return List.of(fee);
    }

    @Override
    public List<LimitRule> findLimitRules() {
      limitReads.incrementAndGet();
      return List.of(limit);
    }
  }
}
